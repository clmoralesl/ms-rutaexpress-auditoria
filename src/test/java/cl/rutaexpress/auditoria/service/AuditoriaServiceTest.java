package cl.rutaexpress.auditoria.service;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.exception.AuditoriaNoEncontradaException;
import cl.rutaexpress.auditoria.exception.HistorialAmbiguoException;
import cl.rutaexpress.auditoria.repository.HistorialAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private HistorialAuditoriaRepository repository;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private AuditoriaService auditoriaService;

    @Captor
    private ArgumentCaptor<HistorialAuditoria> captor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        auditoriaService = new AuditoriaService(repository, objectMapper, meterRegistry);
    }

    private HistorialAuditoria crearEntidad(Long id, String eventoId, Long envioId, String codigo, String estadoAnt, String estadoNvo) {
        HistorialAuditoria entidad = new HistorialAuditoria(
                eventoId,
                envioId,
                codigo,
                estadoAnt,
                estadoNvo,
                "auditor@rutaexpress.cl",
                "AUDITOR",
                LocalDateTime.now(),
                "{\"pesoKg\":4.5}"
        );
        try {
            var field = HistorialAuditoria.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entidad, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entidad;
    }

    private HistorialAuditoria crearEntidad(Long id, String eventoId, Long envioId, String estadoAnt, String estadoNvo) {
        return crearEntidad(id, eventoId, envioId, "RE-TEST-100", estadoAnt, estadoNvo);
    }

    @Test
    @DisplayName("Registrar evento nuevo exitosamente")
    void testRegistrarEventoExitoso() {
        UUID eventoId = UUID.randomUUID();
        Map<String, Object> detalles = new HashMap<>();
        detalles.put("pesoKg", 4.5);

        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                "CREADO",
                "ACEPTADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                detalles
        );

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(false);
        when(repository.save(any(HistorialAuditoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HistorialAuditoria resultado = auditoriaService.registrarEvento(dto);

        assertNotNull(resultado);
        verify(repository, times(1)).save(captor.capture());
        HistorialAuditoria guardado = captor.getValue();
        assertEquals(55L, guardado.getEnvioId());
        assertEquals("ACEPTADO", guardado.getEstadoNuevo());
        assertEquals("CREADO", guardado.getEstadoAnterior());
        assertEquals("operador@rutaexpress.cl", guardado.getUsuarioEmail());
        assertNotNull(guardado.getFechaRegistro());
    }

    @Test
    @DisplayName("Idempotencia: Evento duplicado no se vuelve a insertar")
    void testRegistrarEventoDuplicadoIdempotente() {
        UUID eventoId = UUID.randomUUID();
        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                null,
                "CREADO",
                "sistema@rutaexpress.cl",
                "SISTEMA",
                LocalDateTime.now(),
                null
        );

        HistorialAuditoria existente = crearEntidad(1L, eventoId.toString(), 55L, null, "CREADO");

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(true);
        when(repository.findByEventoId(eventoId.toString())).thenReturn(Optional.of(existente));

        HistorialAuditoria resultado = auditoriaService.registrarEvento(dto);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(repository, never()).save(any(HistorialAuditoria.class));
    }

    @Test
    @DisplayName("Obtener timeline de envio exitoso con orden cronologico")
    void testObtenerTimelineExitoso() {
        HistorialAuditoria e1 = crearEntidad(1L, UUID.randomUUID().toString(), 10L, null, "CREADO");
        HistorialAuditoria e2 = crearEntidad(2L, UUID.randomUUID().toString(), 10L, "CREADO", "ACEPTADO");
        HistorialAuditoria e3 = crearEntidad(3L, UUID.randomUUID().toString(), 10L, "ACEPTADO", "EN_RUTA");

        when(repository.findByEnvioIdOrderByFechaEventoAsc(10L)).thenReturn(List.of(e1, e2, e3));

        TimelineEnvioResponse timeline = auditoriaService.obtenerTimelinePorEnvioId(10L);

        assertNotNull(timeline);
        assertEquals(10L, timeline.envioId());
        assertEquals("RE-TEST-100", timeline.codigoSeguimiento());
        assertEquals(3, timeline.totalEventos());
        assertEquals("CREADO", timeline.timeline().get(0).estadoNuevo());
        assertEquals("EN_RUTA", timeline.timeline().get(2).estadoNuevo());
    }

    @Test
    @DisplayName("Obtener timeline para envio sin historial arroja AuditoriaNoEncontradaException")
    void testObtenerTimelineNoEncontrado() {
        when(repository.findByEnvioIdOrderByFechaEventoAsc(999L)).thenReturn(Collections.emptyList());

        assertThrows(AuditoriaNoEncontradaException.class, () ->
                auditoriaService.obtenerTimelinePorEnvioId(999L));
    }

    @Test
    @DisplayName("Listar eventos paginados con filtros")
    void testListarEventosPaginados() {
        HistorialAuditoria e1 = crearEntidad(1L, UUID.randomUUID().toString(), 10L, null, "CREADO");
        Pageable pageable = PageRequest.of(0, 10);
        Page<HistorialAuditoria> page = new PageImpl<>(List.of(e1), pageable, 1);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<TimelineItemResponse> resultado = auditoriaService.listarEventos("auditor", null, null, "CREADO", pageable);

        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        assertEquals("CREADO", resultado.getContent().get(0).estadoNuevo());
    }

    // ===== Casos Borde del Dictamen Issue #85 =====

    @Test
    @DisplayName("C1: Consulta por par no mezcla envios que comparten el mismo envioId")
    void timelinePorParNoMezclaEnviosConMismoId() {
        HistorialAuditoria e1 = crearEntidad(1L, "evt-1", 1L, "RTX-A", null, "CREADO");
        HistorialAuditoria e2 = crearEntidad(2L, "evt-2", 1L, "RTX-A", "CREADO", "EN_RUTA");

        when(repository.findByEnvioIdAndCodigoSeguimientoOrderByFechaEventoAsc(1L, "RTX-A"))
                .thenReturn(List.of(e1, e2));

        TimelineEnvioResponse response = auditoriaService.obtenerTimeline(1L, "RTX-A");

        assertNotNull(response);
        assertEquals(1L, response.envioId());
        assertEquals("RTX-A", response.codigoSeguimiento());
        assertEquals(2, response.totalEventos());
    }

    @Test
    @DisplayName("C2: Consulta sin codigo para envio con multiples codigos lanza HistorialAmbiguoException 409")
    void timelineSinCodigoConVariosCodigosLanzaHistorialAmbiguo() {
        HistorialAuditoria e1 = crearEntidad(1L, "evt-1", 1L, "RTX-A", null, "CREADO");
        HistorialAuditoria e2 = crearEntidad(2L, "evt-2", 1L, "RE-AUDIT-1", null, "CREADO");

        when(repository.findByEnvioIdOrderByFechaEventoAsc(1L)).thenReturn(List.of(e1, e2));

        HistorialAmbiguoException ex = assertThrows(HistorialAmbiguoException.class, () ->
                auditoriaService.obtenerTimeline(1L, null));

        assertEquals(List.of("RE-AUDIT-1", "RTX-A"), ex.getCodigosSeguimiento());
    }

    @Test
    @DisplayName("C6: Codigo en blanco o espacios se trata como ausente")
    void codigoEnBlancoSeTrataComoAusente() {
        HistorialAuditoria e1 = crearEntidad(1L, "evt-1", 10L, "RTX-2026-000010", null, "CREADO");

        when(repository.findByEnvioIdOrderByFechaEventoAsc(10L)).thenReturn(List.of(e1));

        TimelineEnvioResponse response = auditoriaService.obtenerTimeline(10L, "   ");

        assertNotNull(response);
        assertEquals("RTX-2026-000010", response.codigoSeguimiento());
        verify(repository, never()).findByEnvioIdAndCodigoSeguimientoOrderByFechaEventoAsc(any(), any());
    }

    @Test
    @DisplayName("C7: Codigo con minusculas se acepta y se compara exacto sin modificar mayusculas")
    void codigoConMinusculasSeComparaExacto() {
        String codigoMinuscula = "RE-KAFKA-38fddb3c";
        HistorialAuditoria e1 = crearEntidad(1L, "evt-1", 33L, codigoMinuscula, null, "CREADO");

        when(repository.findByEnvioIdAndCodigoSeguimientoOrderByFechaEventoAsc(33L, codigoMinuscula))
                .thenReturn(List.of(e1));

        TimelineEnvioResponse response = auditoriaService.obtenerTimeline(33L, codigoMinuscula);

        assertNotNull(response);
        assertEquals(codigoMinuscula, response.codigoSeguimiento());
    }

    @Test
    @DisplayName("C9: Evento nuevo con envioId existente y otro codigo se guarda y registra metrica")
    void eventoInconsistenteSeGuardaYGeneraMetrica() {
        UUID eventoId = UUID.randomUUID();
        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                "CREADO",
                "ACEPTADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(false);
        when(repository.existsByEnvioIdAndCodigoSeguimientoNot(55L, "RE-TEST-100")).thenReturn(true);
        when(repository.save(any(HistorialAuditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        HistorialAuditoria guardado = auditoriaService.registrarEvento(dto);

        assertNotNull(guardado);
        verify(repository).save(any(HistorialAuditoria.class));
        double count = meterRegistry.get(AuditoriaService.METRICA_INCONSISTENCIAS)
                .tag("tipo", "envio_id_con_otro_codigo")
                .counter()
                .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("C10: Evento nuevo con codigo existente y otro envioId genera metrica")
    void codigoConOtroEnvioIdGeneraMetrica() {
        UUID eventoId = UUID.randomUUID();
        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                "CREADO",
                "ACEPTADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(false);
        when(repository.existsByEnvioIdAndCodigoSeguimientoNot(55L, "RE-TEST-100")).thenReturn(false);
        when(repository.existsByCodigoSeguimientoAndEnvioIdNot("RE-TEST-100", 55L)).thenReturn(true);
        when(repository.save(any(HistorialAuditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        HistorialAuditoria guardado = auditoriaService.registrarEvento(dto);

        assertNotNull(guardado);
        verify(repository).save(any(HistorialAuditoria.class));
        double count = meterRegistry.get(AuditoriaService.METRICA_INCONSISTENCIAS)
                .tag("tipo", "codigo_con_otro_envio_id")
                .counter()
                .count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("C11: Evento duplicado no incrementa metricas de inconsistencia")
    void duplicadoNoIncrementaMetrica() {
        UUID eventoId = UUID.randomUUID();
        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                null,
                "CREADO",
                "sistema@rutaexpress.cl",
                "SISTEMA",
                LocalDateTime.now(),
                null
        );

        HistorialAuditoria existente = crearEntidad(1L, eventoId.toString(), 55L, null, "CREADO");

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(true);
        when(repository.findByEventoId(eventoId.toString())).thenReturn(Optional.of(existente));

        auditoriaService.registrarEvento(dto);

        assertNull(meterRegistry.find(AuditoriaService.METRICA_INCONSISTENCIAS).counter());
        verify(repository, never()).existsByEnvioIdAndCodigoSeguimientoNot(any(), any());
    }

    @Test
    @DisplayName("C12: Evento consistente no genera metrica de inconsistencia")
    void eventoConsistenteNoGeneraMetrica() {
        UUID eventoId = UUID.randomUUID();
        EventoEnvioKafkaDTO dto = new EventoEnvioKafkaDTO(
                eventoId,
                55L,
                "RE-TEST-100",
                "CREADO",
                "ACEPTADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        when(repository.existsByEventoId(eventoId.toString())).thenReturn(false);
        when(repository.existsByEnvioIdAndCodigoSeguimientoNot(55L, "RE-TEST-100")).thenReturn(false);
        when(repository.existsByCodigoSeguimientoAndEnvioIdNot("RE-TEST-100", 55L)).thenReturn(false);
        when(repository.save(any(HistorialAuditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        auditoriaService.registrarEvento(dto);

        assertNull(meterRegistry.find(AuditoriaService.METRICA_INCONSISTENCIAS).counter());
        verify(repository, times(1)).save(any(HistorialAuditoria.class));
    }

    @Test
    @DisplayName("C13: Evento con envioId o codigo nulo no consulta consistencia")
    void eventoConCamposNulosNoConsultaConsistencia() {
        EventoEnvioKafkaDTO dtoSinCodigo = new EventoEnvioKafkaDTO(
                UUID.randomUUID(),
                55L,
                null,
                null,
                "CREADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        when(repository.existsByEventoId(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditoriaService.registrarEvento(dtoSinCodigo);

        verify(repository, never()).existsByEnvioIdAndCodigoSeguimientoNot(any(), any());
        verify(repository, never()).existsByCodigoSeguimientoAndEnvioIdNot(any(), any());

        EventoEnvioKafkaDTO dtoSinEnvioId = new EventoEnvioKafkaDTO(
                UUID.randomUUID(),
                null,
                "RTX-2026-000001",
                null,
                "CREADO",
                "operador@rutaexpress.cl",
                "OPERADOR",
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        auditoriaService.registrarEvento(dtoSinEnvioId);

        verify(repository, never()).existsByEnvioIdAndCodigoSeguimientoNot(any(), any());
        verify(repository, never()).existsByCodigoSeguimientoAndEnvioIdNot(any(), any());
    }

    @Test
    @DisplayName("C14: Metodo limpiarParaLog sanitiza saltos de linea contra CWE-117")
    void limpiarParaLogReemplazaSaltosDeLinea() {
        assertEquals("CODIGO__TEST", AuditoriaService.limpiarParaLog("CODIGO\r\nTEST"));
        assertEquals("CODIGO_TEST", AuditoriaService.limpiarParaLog("CODIGO\nTEST"));
        assertNull(AuditoriaService.limpiarParaLog(null));
    }

    @Test
    @DisplayName("C15: listarEventos filtra por codigoSeguimiento exacto")
    void listarEventosFiltraPorCodigoExacto() {
        HistorialAuditoria e1 = crearEntidad(1L, "evt-1", 10L, "RTX-2026-000001", null, "CREADO");
        Pageable pageable = PageRequest.of(0, 10);
        Page<HistorialAuditoria> page = new PageImpl<>(List.of(e1), pageable, 1);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<TimelineItemResponse> resultado = auditoriaService.listarEventos(null, null, null, null, "RTX-2026-000001", pageable);

        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>", "RTX'1", "RTX 2026", "RTX_2026", "RTX;DROP"})
    @DisplayName("C5: codigo con caracteres invalidos lanza IllegalArgumentException sin consultar el repositorio")
    void codigoInvalidoLanzaIllegalArgument(String codigo) {
        assertThrows(IllegalArgumentException.class, () -> auditoriaService.obtenerTimeline(1L, codigo));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("C5: codigo de 51 caracteres es invalido y el de 50 es valido")
    void codigoLimiteDeLargo() {
        String cincuenta = "A".repeat(50);
        assertEquals(cincuenta, AuditoriaService.normalizarCodigo("  " + cincuenta + "  "));
        assertThrows(IllegalArgumentException.class, () -> AuditoriaService.normalizarCodigo("A".repeat(51)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("C8: id no positivo lanza IllegalArgumentException sin consultar el repositorio")
    void idNoPositivoLanzaIllegalArgument(long id) {
        assertThrows(IllegalArgumentException.class, () -> auditoriaService.obtenerTimeline(id, null));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("C8: id nulo lanza IllegalArgumentException")
    void idNuloLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> auditoriaService.obtenerTimeline(null, null));
    }

    @Test
    @DisplayName("C5: listarEventos con codigo invalido lanza IllegalArgumentException sin consultar el repositorio")
    void listarEventosConCodigoInvalidoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                auditoriaService.listarEventos(null, null, null, null, "<x>", PageRequest.of(0, 10)));
        verifyNoInteractions(repository);
    }
}
