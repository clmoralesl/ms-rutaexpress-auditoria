package cl.rutaexpress.auditoria.dto;

import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public record TimelineItemResponse(
        Long id,
        String eventoId,
        Long envioId,
        String codigoSeguimiento,
        String estadoAnterior,
        String estadoNuevo,
        String usuarioEmail,
        String usuarioRol,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime fechaEvento,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime fechaRegistro,
        Map<String, Object> detalles
) {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static TimelineItemResponse fromEntity(HistorialAuditoria entity) {
        Map<String, Object> mapDetalles = Collections.emptyMap();
        if (entity.getDetallesJson() != null && !entity.getDetallesJson().isBlank()) {
            try {
                mapDetalles = mapper.readValue(entity.getDetallesJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
                mapDetalles = Collections.singletonMap("raw", entity.getDetallesJson());
            }
        }

        return new TimelineItemResponse(
                entity.getId(),
                entity.getEventoId(),
                entity.getEnvioId(),
                entity.getCodigoSeguimiento(),
                entity.getEstadoAnterior(),
                entity.getEstadoNuevo(),
                entity.getUsuarioEmail(),
                entity.getUsuarioRol(),
                entity.getFechaEvento(),
                entity.getFechaRegistro(),
                mapDetalles
        );
    }
}
