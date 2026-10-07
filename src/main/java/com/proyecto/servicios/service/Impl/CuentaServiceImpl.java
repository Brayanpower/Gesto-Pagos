package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.CuentaMapper;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaCreacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.service.CuentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;

@Service
@Slf4j
public class CuentaServiceImpl implements CuentaService {

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final CuentaMapper cuentaMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CuentaServiceImpl(
            CuentaRepository cuentaRepository,
            ClienteRepository clienteRepository,
            CuentaMapper cuentaMapper) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.cuentaMapper = cuentaMapper;
    }

    @Override
    @Transactional
    public CuentaResponse crearCuenta(CuentaCreacionRequest request) {
        log.info("Creando nueva cuenta bancaria para el cliente ID: {}", request.getClienteId());

        Cliente cliente = clienteRepository.findByIdAndActivoTrue(request.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente activo no encontrado con ID: " + request.getClienteId()));

        BigDecimal saldoInicial = (request.getSaldoInicial() != null) ? request.getSaldoInicial() : BigDecimal.ZERO;
        if (saldoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo");
        }

        String numeroCuenta = generarNumeroCuentaUnico();

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setSaldo(saldoInicial);
        cuenta.setEstatus(EstatusCuenta.ACTIVA);

        Cuenta guardada = cuentaRepository.save(cuenta);
        log.info("Cuenta bancaria {} creada exitosamente para cliente {}", numeroCuenta, cliente.getId());

        return cuentaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse obtenerPorNumeroCuenta(String numeroCuenta) {
        Cuenta cuenta = buscar(numeroCuenta);
        return cuentaMapper.toResponse(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerPorClienteId(Long clienteId) {
        return cuentaMapper.toResponses(cuentaRepository.findByClienteId(clienteId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerPorEstatus(EstatusCuenta estatus) {
        return cuentaMapper.toResponses(cuentaRepository.findByEstatus(estatus));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> listarTodas() {
        return cuentaMapper.toResponses(cuentaRepository.findAll());
    }

    @Override
    @Transactional
    public CuentaResponse actualizarEstatus(String numeroCuenta, CuentaActualizacionRequest request) {
        log.info("Actualizando estatus de cuenta {} a {}", numeroCuenta, request.getEstatus());
        Cuenta cuenta = buscar(numeroCuenta);

        // Regla de negocio: solo un cliente activo puede tener cuentas activas
        if (request.getEstatus() == EstatusCuenta.ACTIVA
                && (cuenta.getCliente() == null || !cuenta.getCliente().isActivo())) {
            throw new IllegalArgumentException(
                    "No se puede activar la cuenta porque el cliente titular no esta activo");
        }

        cuenta.setEstatus(request.getEstatus());
        Cuenta actualizada = cuentaRepository.save(cuenta);
        return cuentaMapper.toResponse(actualizada);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldo(String numeroCuenta) {
        Cuenta cuenta = buscar(numeroCuenta);
        return cuenta.getSaldo();
    }

    private Cuenta buscar(String numeroCuenta) {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro la cuenta bancaria: " + numeroCuenta));
    }

    private String generarNumeroCuentaUnico() {
        String numeroCuenta;
        do {
            long numero = 1000000000L + (long) (secureRandom.nextDouble() * 9000000000L);
            numeroCuenta = String.valueOf(numero);
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        return numeroCuenta;
    }
}
