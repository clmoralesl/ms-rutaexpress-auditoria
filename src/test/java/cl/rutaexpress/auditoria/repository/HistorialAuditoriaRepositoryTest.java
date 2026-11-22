package cl.rutaexpress.auditoria.repository;

import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import cl.rutaexpress.auditoria.exception.HistorialAmbiguoException;
import cl.rutaexpress.auditoria.service.AuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HistorialAuditoriaRepositoryTest {

    @Autowired
    private HistorialAuditoriaRepository repository;

    private HistorialAuditoria crearEvento(String eventoId, Long envioId, String codigo, String estado) {
        return new HistorialAuditoria(
                eventoId,
                envioId,
                codigo,
                null,
                estado,
                "admin@rutaexpress.cl",
                "ADMIN",
                LocalDateTime.now(),
                "{}"
        );
    }

    @Test
    @DisplayName("C1: Filtra por par (envioId, codigoSeguimiento) sin mezclar envios que comparten id")
    void filtraPorParSinMezclarEnviosQueCompartenId() {
        repository.save(crearEvento("evt-real-1", 1L, "RTX-2026-000001", "CREADO"));
        repository.save(crearEvento("evt-real-2", 1L, "RTX-2026-000001", "EN_BODEGA"));
        repository.save(crearEvento("evt-real-3", 1L, "RTX-2026-000001", "EN_RUTA"));

        repository.save(crearEvento("evt-test-1", 1L, "RE-AUDIT-1", "CREADO"));
        repository.save(crearEvento("evt-test-2", 1L, "RE-AUDIT-1", "ENTREGADO"));

        List<HistorialAuditoria> resultado = repository.findByEnvioIdAndCodigoSeguimientoOrderByFechaEventoAsc(1L, "RTX-2026-000001");

        assertEquals(3, resultado.size());
        assertTrue(resultado.stream().allMatch(e -> "RTX-2026-000001".equals(e.getCodigoSeguimiento())));
        assertTrue(resultado.stream().allMatch(e -> Long.valueOf(1L).equals(e.getEnvioId())));
    }

    @Test
    @DisplayName("Detecta si un envioId ya existe con otro codigo de seguimiento")
    void detectaEnvioIdConOtroCodigo() {
        repository.save(crearEvento("evt-1", 10L, "RTX-2026-000010", "CREADO"));

        assertTrue(repository.existsByEnvioIdAndCodigoSeguimientoNot(10L, "OTRO-CODIGO"));
        assertFalse(repository.existsByEnvioIdAndCodigoSeguimientoNot(10L, "RTX-2026-000010"));
        assertFalse(repository.existsByEnvioIdAndCodigoSeguimientoNot(99L, "RTX-2026-000010"));
    }

    @Test
    @DisplayName("Detecta si un codigo de seguimiento ya existe con otro envioId")
    void detectaCodigoConOtroEnvioId() {
        repository.save(crearEvento("evt-2", 20L, "RTX-2026-000020", "CREADO"));

        assertTrue(repository.existsByCodigoSeguimientoAndEnvioIdNot("RTX-2026-000020", 99L));
        assertFalse(repository.existsByCodigoSeguimientoAndEnvioIdNot("RTX-2026-000020", 20L));
        assertFalse(repository.existsByCodigoSeguimientoAndEnvioIdNot("CODIGO-INEXISTENTE", 20L));
    }

    @Test
    @DisplayName("C15, C2, C1: AuditoriaService sobre repositorio real H2 valida filtro, ambiguedad y consulta por par")
    void servicioSobreRepositorioRealValidaFiltroAmbiguedadYPar() {
        AuditoriaService service = new AuditoriaService(repository, new ObjectMapper(), new SimpleMeterRegistry());

        repository.save(crearEvento("evt-real-1", 1L, "RTX-2026-000001", "CREADO"));
        repository.save(crearEvento("evt-real-2", 1L, "RTX-2026-000001", "EN_RUTA"));
        repository.save(crearEvento("evt-test-1", 1L, "RE-AUDIT-1", "CREADO"));
        repository.save(crearEvento("evt-real-3", 2L, "RTX-2026-000002", "CREADO"));

        // C15: listarEventos filtra por codigo exacto sobre Specification real
        Page<TimelineItemResponse> pagina = service.listarEventos(null, null, null, null, "RTX-2026-000001", PageRequest.of(0, 20));
        assertEquals(2, pagina.getTotalElements());
        assertTrue(pagina.getContent().stream().allMatch(item -> "RTX-2026-000001".equals(item.codigoSeguimiento())));

        // C2: obtenerTimeline sin codigo con multiples codigos lanza HistorialAmbiguoException en orden alfabetico
        HistorialAmbiguoException ex = assertThrows(HistorialAmbiguoException.class, () -> service.obtenerTimeline(1L, null));
        assertEquals(List.of("RE-AUDIT-1", "RTX-2026-000001"), ex.getCodigosSeguimiento());

        // C1: obtenerTimeline con par devuelve solo los eventos correspondientes
        TimelineEnvioResponse timeline = service.obtenerTimeline(1L, "RTX-2026-000001");
        assertNotNull(timeline);
        assertEquals(1L, timeline.envioId());
        assertEquals("RTX-2026-000001", timeline.codigoSeguimiento());
        assertEquals(2, timeline.totalEventos());
    }
}
