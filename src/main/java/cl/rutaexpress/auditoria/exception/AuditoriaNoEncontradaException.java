package cl.rutaexpress.auditoria.exception;

public class AuditoriaNoEncontradaException extends RuntimeException {

    public AuditoriaNoEncontradaException(String mensaje) {
        super(mensaje);
    }

    public static AuditoriaNoEncontradaException paraEnvio(Long envioId) {
        return new AuditoriaNoEncontradaException("No se encontro historial de auditoria para el envio con ID: " + envioId);
    }
}
