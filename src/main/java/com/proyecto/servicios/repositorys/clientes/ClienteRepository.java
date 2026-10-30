package com.proyecto.servicios.repositorys.clientes;

import com.proyecto.servicios.entity.clientes.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByCurp(String curp);

    Optional<Cliente> findByRfc(String rfc);

    Optional<Cliente> findByCorreo(String correo);

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreo(String correo);

    List<Cliente> findByActivoTrue();

    Optional<Cliente> findByIdAndActivoTrue(Long id);

    boolean existsByCuentasNumeroCuenta(String numeroCuenta);

    @Query("select count(c) > 0 from Cuenta c "
            + "where c.numeroCuenta = :numeroCuenta "
            + "and c.estatus = com.proyecto.servicios.entity.cuentas.EstatusCuenta.ACTIVA")
    boolean cuentaActiva(@Param("numeroCuenta") String numeroCuenta);

    @Query("select c.saldo from Cuenta c where c.numeroCuenta = :numeroCuenta")
    Optional<BigDecimal> obtenerSaldo(@Param("numeroCuenta") String numeroCuenta);

}
