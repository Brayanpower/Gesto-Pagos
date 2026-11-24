package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.gestopago.TransaccionServicio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionServicioRepository extends JpaRepository<TransaccionServicio, Long>,
        JpaSpecificationExecutor<TransaccionServicio> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransaccionServicio t WHERE t.id = :id")
    Optional<TransaccionServicio> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cliente", "cuenta"})
    Optional<TransaccionServicio> findByClaveIdempotencia(String claveIdempotencia);

    @EntityGraph(attributePaths = {"cliente", "cuenta"})
    Optional<TransaccionServicio> findByUpc(String upc);

    boolean existsByUpc(String upc);

    @Query("SELECT count(t) > 0 FROM TransaccionServicio t "
            + "WHERE t.cuenta.id = :cuentaId "
            + "AND t.idServicio = :idServicio "
            + "AND t.idProducto = :idProducto "
            + "AND t.monto = :monto "
            + "AND COALESCE(t.telefono, '') = :telefono "
            + "AND COALESCE(t.referencia, '') = :referencia "
            + "AND (t.estatus IN (com.proyecto.servicios.entity.gestopago.EstatusTransaccion.EN_PROCESO, "
            + "                   com.proyecto.servicios.entity.gestopago.EstatusTransaccion.PENDIENTE_CONCILIACION) "
            + "     OR (t.estatus = com.proyecto.servicios.entity.gestopago.EstatusTransaccion.EXITOSA "
            + "         AND t.fechaCreacion >= :ventanaDesde))")
    boolean existeOperacionDuplicadaReciente(
            @Param("cuentaId") Long cuentaId,
            @Param("idServicio") Integer idServicio,
            @Param("idProducto") Integer idProducto,
            @Param("monto") BigDecimal monto,
            @Param("telefono") String telefono,
            @Param("referencia") String referencia,
            @Param("ventanaDesde") LocalDateTime ventanaDesde);

    List<TransaccionServicio> findTop20ByEstatusAndIntentosConciliacionLessThanOrderByFechaCreacionAsc(
            EstatusTransaccion estatus, Integer maxIntentos);

    List<TransaccionServicio> findTop20ByEstatusAndFechaCreacionBeforeOrderByFechaCreacionAsc(
            EstatusTransaccion estatus, LocalDateTime limite);

    boolean existsByCuentaIdAndEstatusIn(Long cuentaId, Collection<EstatusTransaccion> estatuses);

    boolean existsByClienteIdAndEstatusIn(Long clienteId, Collection<EstatusTransaccion> estatuses);
}
