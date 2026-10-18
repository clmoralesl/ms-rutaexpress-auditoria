package cl.rutaexpress.auditoria.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "HISTORIAL_AUDITORIA",
        schema = "USER_AUDITORIA",
        indexes = {
                @Index(name = "idx_auditoria_envio", columnList = "envio_id"),
                @Index(name = "idx_auditoria_usuario", columnList = "usuario_email"),
                @Index(name = "idx_auditoria_fecha", columnList = "fecha_evento")
        }
)
public class HistorialAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true, length = 64)
    private String eventoId;

    @Column(name = "envio_id", nullable = false)
    private Long envioId;

    @Column(name = "codigo_seguimiento", nullable = false, length = 50)
    private String codigoSeguimiento;

    @Column(name = "estado_anterior", length = 30)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private String estadoNuevo;

    @Column(name = "usuario_email", nullable = false, length = 100)
    private String usuarioEmail;

    @Column(name = "usuario_rol", nullable = false, length = 50)
    private String usuarioRol;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @Column(name = "detalles_json", length = 4000)
    private String detallesJson;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    protected HistorialAuditoria() {
        // Constructor requerido por JPA
    }

    public HistorialAuditoria(
            String eventoId,
            Long envioId,
            String codigoSeguimiento,
            String estadoAnterior,
            String estadoNuevo,
            String usuarioEmail,
            String usuarioRol,
            LocalDateTime fechaEvento,
            String detallesJson) {
        this.eventoId = (eventoId != null) ? eventoId : UUID.randomUUID().toString();
        this.envioId = envioId;
        this.codigoSeguimiento = codigoSeguimiento;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioEmail = usuarioEmail;
        this.usuarioRol = usuarioRol;
        this.fechaEvento = (fechaEvento != null) ? fechaEvento : LocalDateTime.now();
        this.detallesJson = detallesJson;
        this.fechaRegistro = LocalDateTime.now();
    }

    // Getters inmutables (sin setters para garantizar politica de solo lectura/insercion)

    public Long getId() {
        return id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public Long getEnvioId() {
        return envioId;
    }

    public String getCodigoSeguimiento() {
        return codigoSeguimiento;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public String getUsuarioRol() {
        return usuarioRol;
    }

    public LocalDateTime getFechaEvento() {
        return fechaEvento;
    }

    public String getDetallesJson() {
        return detallesJson;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
