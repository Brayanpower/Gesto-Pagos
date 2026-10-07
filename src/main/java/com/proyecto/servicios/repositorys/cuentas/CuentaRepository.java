package com.proyecto.servicios.repositorys.cuentas;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    List<Cuenta> findByClienteId(Long clienteId);

    List<Cuenta> findByEstatus(EstatusCuenta estatus);

    List<Cuenta> findByClienteIdAndEstatus(Long clienteId, EstatusCuenta estatus);

    boolean existsByNumeroCuenta(String numeroCuenta);
}
