package cl.rutaexpress.auditoria.controller;

import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.exception.AuditoriaNoEncontradaException;
import cl.rutaexpress.auditoria.service.AuditoriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuditoriaController.class, SaludController.class})
class AuditoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditoriaService auditoriaService;

    @Test
    @DisplayName("GET /api/auditoria/envios/{id} exitoso retorna 200 con timeline")
    void testObtenerTimelineExitoso() throws Exception {
        TimelineItemResponse item = new TimelineItemResponse(
                1L,
                "evt-123",
                25L,
                "RE-AUDIT-001",
                null,
                "CREADO",
                "admin@rutaexpress.cl",
                "ADMIN",
                LocalDateTime.now(),
                LocalDateTime.now(),
                Map.of("pesoKg", 3.2)
        );

        TimelineEnvioResponse response = new TimelineEnvioResponse(
                25L,
                "RE-AUDIT-001",
                1,
                List.of(item)
        );

        when(auditoriaService.obtenerTimelinePorEnvioId(25L)).thenReturn(response);

        mockMvc.perform(get("/api/auditoria/envios/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envioId").value(25))
                .andExpect(jsonPath("$.codigoSeguimiento").value("RE-AUDIT-001"))
                .andExpect(jsonPath("$.totalEventos").value(1))
                .andExpect(jsonPath("$.timeline[0].estadoNuevo").value("CREADO"));
    }

    @Test
    @DisplayName("GET /api/auditoria/envios/{id} no encontrado retorna 404")
    void testObtenerTimelineNoEncontrado() throws Exception {
        when(auditoriaService.obtenerTimelinePorEnvioId(99L))
                .thenThrow(AuditoriaNoEncontradaException.paraEnvio(99L));

        mockMvc.perform(get("/api/auditoria/envios/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Recurso No Encontrado"));
    }

    @Test
    @DisplayName("GET /api/auditoria/eventos retorna lista paginada")
    void testListarEventos() throws Exception {
        TimelineItemResponse item = new TimelineItemResponse(
                1L,
                "evt-123",
                25L,
                "RE-AUDIT-001",
                null,
                "CREADO",
                "admin@rutaexpress.cl",
                "ADMIN",
                LocalDateTime.now(),
                LocalDateTime.now(),
                Collections.emptyMap()
        );

        when(auditoriaService.listarEventos(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/auditoria/eventos")
                        .param("usuario", "admin")
                        .param("estadoNuevo", "CREADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estadoNuevo").value("CREADO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/publico/salud retorna estado DISPONIBLE")
    void testEndpointSalud() throws Exception {
        mockMvc.perform(get("/api/publico/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-auditoria"))
                .andExpect(jsonPath("$.puerto").value(8084));
    }
}
