package cl.rutaexpress.auditoria.repository;

import cl.rutaexpress.auditoria.entity.HistorialAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistorialAuditoriaRepository extends JpaRepository<HistorialAuditoria, Long>, JpaSpecificationExecutor<HistorialAuditoria> {

    List<HistorialAuditoria> findByEnvioIdOrderByFechaEventoAsc(Long envioId);

    List<HistorialAuditoria> findByCodigoSeguimientoOrderByFechaEventoAsc(String codigoSeguimiento);

    boolean existsByEventoId(String eventoId);

    Optional<HistorialAuditoria> findByEventoId(String eventoId);
}
