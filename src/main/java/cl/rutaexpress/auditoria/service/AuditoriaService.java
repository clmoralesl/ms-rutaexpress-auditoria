package cl.rutaexpress.auditoria.service;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.exception.AuditoriaNoEncontradaException;
import cl.rutaexpress.auditoria.repository.HistorialAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final HistorialAuditoriaRepository repository;
    private final ObjectMapper objectMapper;

    public AuditoriaService(HistorialAuditoriaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

    public TimelineEnvioResponse obtenerTimelinePorEnvioId(Long envioId) {
        List<HistorialAuditoria> eventos = repository.findByEnvioIdOrderByFechaEventoAsc(envioId);
        if (eventos.isEmpty()) {
            throw AuditoriaNoEncontradaException.paraEnvio(envioId);
        }

        String codigoSeguimiento = eventos.get(0).getCodigoSeguimiento();
        List<TimelineItemResponse> items = eventos.stream()
                .map(TimelineItemResponse::fromEntity)
                .toList();

        return new TimelineEnvioResponse(
                envioId,
                codigoSeguimiento,
                items.size(),
                items
        );
    }

    public Page<TimelineItemResponse> listarEventos(
            String usuario,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            String estadoNuevo,
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

        return repository.findAll(spec, pageable)
                .map(TimelineItemResponse::fromEntity);
    }
}
