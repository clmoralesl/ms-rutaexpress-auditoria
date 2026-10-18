package cl.rutaexpress.auditoria.service;

import cl.rutaexpress.auditoria.dto.EventoEnvioKafkaDTO;
import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.exception.AuditoriaNoEncontradaException;
import cl.rutaexpress.auditoria.repository.HistorialAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private AuditoriaService auditoriaService;

    @Captor
    private ArgumentCaptor<HistorialAuditoria> captor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditoriaService = new AuditoriaService(repository, objectMapper);
    }

    private HistorialAuditoria crearEntidad(Long id, String eventoId, Long envioId, String estadoAnt, String estadoNvo) {
        HistorialAuditoria entidad = new HistorialAuditoria(
                eventoId,
                envioId,
                "RE-TEST-100",
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
}
