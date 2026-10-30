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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de CuentaServiceImpl")
class CuentaServiceImplTest {

    @Mock private CuentaRepository cuentaRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private CuentaMapper cuentaMapper;

    @InjectMocks
    private CuentaServiceImpl servicio;

    @Test
    @DisplayName("Crear cuenta para cliente activo genera numero unico de 10 digitos y saldo inicial")
    void crearCuenta_exito() {
        Cliente cliente = clienteActivo(1L);
        when(clienteRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaMapper.toResponse(any(Cuenta.class))).thenReturn(new CuentaResponse());

        CuentaCreacionRequest request = new CuentaCreacionRequest(1L, new BigDecimal("500.00"));
        servicio.crearCuenta(request);

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).save(captor.capture());
        Cuenta cuenta = captor.getValue();
        assertEquals(EstatusCuenta.ACTIVA, cuenta.getEstatus());
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        assertEquals(10, cuenta.getNumeroCuenta().length());
    }

    @Test
    @DisplayName("Crear cuenta con saldo negativo lanza IllegalArgumentException")
    void crearCuenta_saldoNegativo_lanzaExcepcion() {
        when(clienteRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(clienteActivo(1L)));

        CuentaCreacionRequest request = new CuentaCreacionRequest(1L, new BigDecimal("-10.00"));

        assertThrows(IllegalArgumentException.class, () -> servicio.crearCuenta(request));
        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    @DisplayName("Crear cuenta para cliente inexistente o inactivo lanza ResourceNotFoundException")
    void crearCuenta_clienteInactivo_lanzaExcepcion() {
        when(clienteRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

        CuentaCreacionRequest request = new CuentaCreacionRequest(99L, BigDecimal.TEN);

        assertThrows(ResourceNotFoundException.class, () -> servicio.crearCuenta(request));
    }

    @Test
    @DisplayName("Activar cuenta de un cliente inactivo es rechazado")
    void actualizarEstatus_activarConClienteInactivo_lanzaExcepcion() {
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setActivo(false);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setEstatus(EstatusCuenta.INACTIVA);
        cuenta.setCliente(cliente);

        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        CuentaActualizacionRequest request = new CuentaActualizacionRequest(EstatusCuenta.ACTIVA);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizarEstatus("1234567890", request));
        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    @DisplayName("Activar cuenta de un cliente activo es permitido")
    void actualizarEstatus_activarConClienteActivo_actualiza() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setEstatus(EstatusCuenta.INACTIVA);
        cuenta.setCliente(clienteActivo(1L));

        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);
        when(cuentaMapper.toResponse(cuenta)).thenReturn(new CuentaResponse());

        servicio.actualizarEstatus("1234567890", new CuentaActualizacionRequest(EstatusCuenta.ACTIVA));

        assertEquals(EstatusCuenta.ACTIVA, cuenta.getEstatus());
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("Consultar saldo devuelve el saldo de la cuenta")
    void consultarSaldo_devuelveSaldo() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setSaldo(new BigDecimal("2500.50"));
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        BigDecimal saldo = servicio.consultarSaldo("1234567890");

        assertEquals(new BigDecimal("2500.50"), saldo);
    }

    @Test
    @DisplayName("Consultar saldo de cuenta inexistente lanza ResourceNotFoundException")
    void consultarSaldo_inexistente_lanzaExcepcion() {
        when(cuentaRepository.findByNumeroCuenta("0000000000")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> servicio.consultarSaldo("0000000000"));
    }

    private static Cliente clienteActivo(Long id) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setActivo(true);
        return cliente;
    }
}
