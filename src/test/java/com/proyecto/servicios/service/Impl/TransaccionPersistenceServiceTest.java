package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.gestopago.TransaccionServicio;
import com.proyecto.servicios.exception.CuentaNoOperativaException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.exception.SaldoInsuficienteException;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de TransaccionPersistenceService")
class TransaccionPersistenceServiceTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TransaccionServicioRepository transaccionRepository;

    @InjectMocks
    private TransaccionPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(persistenceService, "duplicateWindowSeconds", 60L);
    }

    @Test
    @DisplayName("Descuenta saldo con bloqueo pesimista y crea transaccion EN_PROCESO")
    void reservarYDebitarSaldo_exito() {
        Cuenta cuenta = cuentaActiva(10L, "1234567890", new BigDecimal("500.00"));
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.findByClaveIdempotencia("idem-0001")).thenReturn(Optional.empty());
        when(transaccionRepository.existeOperacionDuplicadaReciente(
                anyLong(), anyInt(), anyInt(), any(), anyString(), anyString(), anyList(), any(), any()))
                .thenReturn(false);
        when(transaccionRepository.existsByUpc(anyString())).thenReturn(false);
        when(transaccionRepository.save(any(TransaccionServicio.class))).thenAnswer(inv -> {
            TransaccionServicio tx = inv.getArgument(0);
            tx.setId(100L);
            return tx;
        });

        CatalogoProducto prod = new CatalogoProducto();
        prod.setServicio("TELCEL");
        prod.setNombreProducto("Recarga 100");

        TransaccionServicio tx = persistenceService.reservarYDebitarSaldo(
                10L, "1234567890", "idem-0001", prod, 2284, 14302,
                new BigDecimal("100.00"), "5512345678", null
        );

        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
        assertEquals(new BigDecimal("500.00"), tx.getSaldoAnterior());
        assertEquals(new BigDecimal("400.00"), tx.getSaldoPosterior());
        assertEquals(EstatusTransaccion.EN_PROCESO, tx.getEstatus());
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("Cuenta inexistente lanza ResourceNotFoundException")
    void reservarYDebitarSaldo_cuentaInexistente() {
        when(cuentaRepository.findByNumeroCuentaForUpdate("0000000000")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                persistenceService.reservarYDebitarSaldo(
                        10L, "0000000000", "idem-0001", new CatalogoProducto(),
                        2284, 14302, new BigDecimal("100.00"), "5512345678", null));
    }

    @Test
    @DisplayName("Cuenta de otro cliente lanza AccessDeniedException (403)")
    void reservarYDebitarSaldo_cuentaAjena() {
        Cuenta cuentaDeOtro = cuentaActiva(99L, "1234567890", new BigDecimal("500.00"));
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(cuentaDeOtro));

        assertThrows(AccessDeniedException.class, () ->
                persistenceService.reservarYDebitarSaldo(
                        10L, "1234567890", "idem-0001", new CatalogoProducto(),
                        2284, 14302, new BigDecimal("100.00"), "5512345678", null));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cuenta bloqueada o inactiva lanza CuentaNoOperativaException")
    void reservarYDebitarSaldo_cuentaBloqueada() {
        Cuenta bloqueada = cuentaActiva(10L, "1234567890", new BigDecimal("500.00"));
        bloqueada.setEstatus(EstatusCuenta.BLOQUEADA);
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(bloqueada));

        assertThrows(CuentaNoOperativaException.class, () ->
                persistenceService.reservarYDebitarSaldo(
                        10L, "1234567890", "idem-0001", new CatalogoProducto(),
                        2284, 14302, new BigDecimal("100.00"), "5512345678", null));
    }

    @Test
    @DisplayName("Saldo insuficiente lanza SaldoInsuficienteException sin modificar saldo")
    void reservarYDebitarSaldo_saldoInsuficiente() {
        Cuenta cuenta = cuentaActiva(10L, "1234567890", new BigDecimal("50.00"));
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.findByClaveIdempotencia("idem-0001")).thenReturn(Optional.empty());
        when(transaccionRepository.existeOperacionDuplicadaReciente(
                anyLong(), anyInt(), anyInt(), any(), anyString(), anyString(), anyList(), any(), any()))
                .thenReturn(false);

        assertThrows(SaldoInsuficienteException.class, () ->
                persistenceService.reservarYDebitarSaldo(
                        10L, "1234567890", "idem-0001", new CatalogoProducto(),
                        2284, 14302, new BigDecimal("100.00"), "5512345678", null));

        assertEquals(new BigDecimal("50.00"), cuenta.getSaldo());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Operacion identica reciente en ventana temporal lanza DuplicateResourceException")
    void reservarYDebitarSaldo_operacionDuplicadaReciente() {
        Cuenta cuenta = cuentaActiva(10L, "1234567890", new BigDecimal("500.00"));
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.findByClaveIdempotencia("idem-0002")).thenReturn(Optional.empty());
        when(transaccionRepository.existeOperacionDuplicadaReciente(
                anyLong(), anyInt(), anyInt(), any(), anyString(), anyString(), anyList(), any(), any()))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                persistenceService.reservarYDebitarSaldo(
                        10L, "1234567890", "idem-0002", new CatalogoProducto(),
                        2284, 14302, new BigDecimal("100.00"), "5512345678", null));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("reversarCargo restituye el saldo una unica vez (idempotente ante llamadas repetidas)")
    void reversarCargo_esIdempotente() {
        Cuenta cuenta = cuentaActiva(10L, "1234567890", new BigDecimal("400.00"));
        TransaccionServicio tx = new TransaccionServicio();
        tx.setId(55L);
        tx.setNumeroCuenta("1234567890");
        tx.setUpc("1122334455");
        tx.setMonto(new BigDecimal("100.00"));
        tx.setEstatus(EstatusTransaccion.EN_PROCESO);

        when(transaccionRepository.findById(55L)).thenReturn(Optional.of(tx));
        when(cuentaRepository.findByNumeroCuentaForUpdate("1234567890")).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.save(any(TransaccionServicio.class))).thenAnswer(inv -> inv.getArgument(0));

        persistenceService.reversarCargo(55L, EstatusTransaccion.RECHAZADA, "05", "Rechazada");
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        assertEquals(EstatusTransaccion.RECHAZADA, tx.getEstatus());

        // Segunda llamada sobre la misma transaccion ya reversada NO vuelve a abonar
        persistenceService.reversarCargo(55L, EstatusTransaccion.RECHAZADA, "05", "Rechazada");
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        verify(cuentaRepository, times(1)).save(cuenta);
    }

    private static Cuenta cuentaActiva(Long clienteId, String numeroCuenta, BigDecimal saldo) {
        Cliente cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setActivo(true);

        Cuenta cuenta = new Cuenta();
        cuenta.setId(1L);
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setSaldo(saldo);
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        return cuenta;
    }
}

