package cl.rutaexpress.auditoria.controller;

import cl.rutaexpress.auditoria.dto.TimelineEnvioResponse;
import cl.rutaexpress.auditoria.dto.TimelineItemResponse;
import cl.rutaexpress.auditoria.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/envios/{id}")
    public ResponseEntity<TimelineEnvioResponse> obtenerTimelinePorEnvio(@PathVariable Long id) {
        TimelineEnvioResponse response = auditoriaService.obtenerTimelinePorEnvioId(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/eventos")
    public ResponseEntity<Page<TimelineItemResponse>> listarEventos(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta,
            @RequestParam(required = false) String estadoNuevo,
            @PageableDefault(size = 20, sort = "fechaEvento", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TimelineItemResponse> response = auditoriaService.listarEventos(
                usuario,
                fechaDesde,
                fechaHasta,
                estadoNuevo,
                pageable
        );
        return ResponseEntity.ok(response);
    }
}
