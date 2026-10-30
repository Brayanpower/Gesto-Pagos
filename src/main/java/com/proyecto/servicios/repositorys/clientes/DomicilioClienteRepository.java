package com.proyecto.servicios.repositorys.clientes;

import com.proyecto.servicios.entity.clientes.DomicilioCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DomicilioClienteRepository extends JpaRepository<DomicilioCliente, Long> {

    Optional<DomicilioCliente> findByClienteId(Long clienteId);
}
