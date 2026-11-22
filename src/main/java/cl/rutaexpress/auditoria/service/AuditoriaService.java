package cl.rutaexpress.auditoria.service;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.exception.AuditoriaNoEncontradaException;
import cl.rutaexpress.auditoria.exception.HistorialAmbiguoException;
import cl.rutaexpress.auditoria.repository.HistorialAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    static final String METRICA_INCONSISTENCIAS = "auditoria.eventos.inconsistentes";
    private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Za-z0-9-]{1,50}$");

    private final HistorialAuditoriaRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AuditoriaService(HistorialAuditoriaRepository repository, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public HistorialAuditoria registrarEvento(EventoEnvioKafkaDTO dto) {
        if (dto == null) {
            log.warn("Se recibio un DTO de evento de auditoria nulo. Se omite registro.");
            return null;
        }

        String eventoIdStr = dto.eventoId() != null ? dto.eventoId().toString() : null;

        // Idempotencia: si el evento ya fue registrado previamente, no duplicar en el timeline
        if (eventoIdStr != null && repository.existsByEventoId(eventoIdStr)) {
            log.info("Evento ya procesado previamente en auditoria [eventoId={}, envioId={}]. Se descarta por idempotencia.",
                    eventoIdStr, dto.envioId());
            return repository.findByEventoId(eventoIdStr).orElse(null);
        }

        detectarInconsistencias(dto, eventoIdStr);

        String detallesJson = null;
        if (dto.detalles() != null && !dto.detalles().isEmpty()) {
            try {
                detallesJson = objectMapper.writeValueAsString(dto.detalles());
            } catch (Exception ex) {
                log.error("Error al serializar detalles JSON del evento [eventoId={}]: {}", eventoIdStr, ex.getMessage());
                detallesJson = dto.detalles().toString();
            }
        }

        HistorialAuditoria entidad = new HistorialAuditoria(
                eventoIdStr,
                dto.envioId(),
                dto.codigoSeguimiento(),
                dto.estadoAnterior(),
                dto.estadoNuevo(),
                dto.usuarioEmail(),
                dto.usuarioRol(),
                dto.timestamp(),
                detallesJson
        );

        HistorialAuditoria guardado = repository.save(entidad);
        log.info("Evento de auditoria registrado inmutablemente [id={}, eventoId={}, envioId={}, estadoNuevo={}]",
                guardado.getId(), guardado.getEventoId(), guardado.getEnvioId(), guardado.getEstadoNuevo());

        return guardado;
    }

    private void detectarInconsistencias(EventoEnvioKafkaDTO dto, String eventoId) {
        if (dto.envioId() == null || dto.codigoSeguimiento() == null) {
            return;
        }
        if (repository.existsByEnvioIdAndCodigoSeguimientoNot(dto.envioId(), dto.codigoSeguimiento())) {
            log.warn("Evento de auditoria inconsistente: el envioId ya esta registrado con otro codigo de seguimiento. Se registra igualmente [eventoId={}, envioId={}, codigoSeguimiento={}]",
                    eventoId, dto.envioId(), limpiarParaLog(dto.codigoSeguimiento()));
            meterRegistry.counter(METRICA_INCONSISTENCIAS, "tipo", "envio_id_con_otro_codigo").increment();
        }
        if (repository.existsByCodigoSeguimientoAndEnvioIdNot(dto.codigoSeguimiento(), dto.envioId())) {
            log.warn("Evento de auditoria inconsistente: el codigo de seguimiento ya esta registrado con otro envioId. Se registra igualmente [eventoId={}, envioId={}, codigoSeguimiento={}]",
                    eventoId, dto.envioId(), limpiarParaLog(dto.codigoSeguimiento()));
            meterRegistry.counter(METRICA_INCONSISTENCIAS, "tipo", "codigo_con_otro_envio_id").increment();
        }
    }

    static String limpiarParaLog(String valor) {
        return valor == null ? null : valor.replace('\r', '_').replace('\n', '_');
    }

    public TimelineEnvioResponse obtenerTimelinePorEnvioId(Long envioId) {
        return obtenerTimeline(envioId, null);
    }

    public TimelineEnvioResponse obtenerTimeline(Long envioId, String codigoSeguimiento) {
        if (envioId == null || envioId < 1) {
            throw new IllegalArgumentException("El identificador del envio debe ser un numero positivo.");
        }
        String codigo = normalizarCodigo(codigoSeguimiento);

        List<HistorialAuditoria> eventos = (codigo != null)
                ? repository.findByEnvioIdAndCodigoSeguimientoOrderByFechaEventoAsc(envioId, codigo)
                : repository.findByEnvioIdOrderByFechaEventoAsc(envioId);

        if (eventos.isEmpty()) {
            throw (codigo != null)
                    ? AuditoriaNoEncontradaException.paraEnvioYCodigo(envioId, codigo)
                    : AuditoriaNoEncontradaException.paraEnvio(envioId);
        }

        List<String> codigos = eventos.stream()
                .map(HistorialAuditoria::getCodigoSeguimiento)
                .distinct()
                .sorted()
                .toList();
        if (codigos.size() > 1) {
            throw new HistorialAmbiguoException(envioId, codigos);
        }

        List<TimelineItemResponse> items = eventos.stream().map(TimelineItemResponse::fromEntity).toList();
        return new TimelineEnvioResponse(envioId, codigos.get(0), items.size(), items);
    }

    static String normalizarCodigo(String codigoSeguimiento) {
        if (codigoSeguimiento == null || codigoSeguimiento.isBlank()) {
            return null;
        }
        String codigo = codigoSeguimiento.trim();
        if (!PATRON_CODIGO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("El codigo de seguimiento solo admite letras, digitos y guiones, con un maximo de 50 caracteres.");
        }
        return codigo;
    }

    public Page<TimelineItemResponse> listarEventos(
            String usuario,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            String estadoNuevo,
            Pageable pageable) {
        return listarEventos(usuario, fechaDesde, fechaHasta, estadoNuevo, null, pageable);
    }

    public Page<TimelineItemResponse> listarEventos(
            String usuario,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            String estadoNuevo,
            String codigoSeguimiento,
            Pageable pageable) {

        Specification<HistorialAuditoria> spec = Specification.where(null);

        if (usuario != null && !usuario.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("usuarioEmail")), "%" + usuario.toLowerCase() + "%"));
        }

        if (fechaDesde != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("fechaEvento"), fechaDesde));
        }

        if (fechaHasta != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("fechaEvento"), fechaHasta));
        }

        if (estadoNuevo != null && !estadoNuevo.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("estadoNuevo"), estadoNuevo.toUpperCase()));
        }

        String codigo = normalizarCodigo(codigoSeguimiento);
        if (codigo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("codigoSeguimiento"), codigo));
        }

        return repository.findAll(spec, pageable)
                .map(TimelineItemResponse::fromEntity);
    }
}
