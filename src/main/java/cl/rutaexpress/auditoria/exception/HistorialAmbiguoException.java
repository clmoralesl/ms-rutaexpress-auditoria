package cl.rutaexpress.auditoria.exception;

import java.util.List;

public class HistorialAmbiguoException extends RuntimeException {

    private final transient List<String> codigosSeguimiento;

    public HistorialAmbiguoException(Long envioId, List<String> codigosSeguimiento) {
        super("El envio con ID " + envioId + " tiene eventos con " + codigosSeguimiento.size()
                + " codigos de seguimiento distintos. Indique el parametro codigoSeguimiento.");
        this.codigosSeguimiento = List.copyOf(codigosSeguimiento);
    }

    public List<String> getCodigosSeguimiento() {
        return codigosSeguimiento;
    }
}
