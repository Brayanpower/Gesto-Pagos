package com.proyecto.servicios.repositorys.cuentas;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    @Override
    @EntityGraph(attributePaths = "cliente")
    List<Cuenta> findAll();

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findWithClienteByNumeroCuenta(String numeroCuenta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuenta c JOIN FETCH c.cliente WHERE c.numeroCuenta = :numeroCuenta")
    Optional<Cuenta> findByNumeroCuentaForUpdate(@Param("numeroCuenta") String numeroCuenta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuenta c JOIN FETCH c.cliente WHERE c.cliente.id = :clienteId")
    Optional<Cuenta> findByClienteIdForUpdate(@Param("clienteId") Long clienteId);

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findByClienteId(Long clienteId);

    @EntityGraph(attributePaths = "cliente")
    List<Cuenta> findByEstatusOrderByFechaCreacionAsc(EstatusCuenta estatus);

    @EntityGraph(attributePaths = "cliente")
    List<Cuenta> findByEstatus(EstatusCuenta estatus);

    boolean existsByClienteId(Long clienteId);

    boolean existsByNumeroCuenta(String numeroCuenta);
}
