package cl.rutaexpress.auditoria.dto;

import java.util.List;

public record TimelineEnvioResponse(
        Long envioId,
        String codigoSeguimiento,
        int totalEventos,
        List<TimelineItemResponse> timeline
) {
}
