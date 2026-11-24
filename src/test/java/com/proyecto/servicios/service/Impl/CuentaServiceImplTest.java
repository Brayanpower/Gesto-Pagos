package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.cuentas.TipoCuenta;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.CuentaMapper;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de CuentaServiceImpl")
class CuentaServiceImplTest {

    @Mock private CuentaRepository cuentaRepository;
    @Mock private CuentaMapper cuentaMapper;
    @Mock private AuthenticatedUserService authenticatedUserService;
    @Mock private TransaccionServicioRepository transaccionServicioRepository;

    @InjectMocks
    private CuentaServiceImpl servicio;

    @Test
    @DisplayName("ADMIN revisa y aprueba una cuenta de onboarding PENDIENTE_REVISION -> ACTIVA")
    void revisarCuenta_adminAprueba_actualizaYRegistraAuditoria() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setTipoCuenta(TipoCuenta.AHORRO);
        cuenta.setEstatus(EstatusCuenta.PENDIENTE_REVISION);
        cuenta.setCliente(clienteActivo(1L));

        when(cuentaRepository.findWithClienteByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);
        when(cuentaMapper.toResponse(cuenta)).thenReturn(new CuentaResponse());

        CuentaActualizacionRequest req = new CuentaActualizacionRequest(EstatusCuenta.ACTIVA, "Documentacion validada");
        servicio.revisarCuenta("1234567890", req);

        assertEquals(EstatusCuenta.ACTIVA, cuenta.getEstatus());
        assertEquals("admin@banco.com", cuenta.getRevisadoPor());
        assertEquals("Documentacion validada", cuenta.getObservacionRevision());
        assertNotNull(cuenta.getFechaRevision());
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("No-ADMIN que intenta revisar una cuenta recibe AccessDeniedException")
    void revisarCuenta_clienteIntentaRevisar_lanzaAccessDenied() {
        Usuario cliente = usuarioCliente(1L);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(cliente);
        when(authenticatedUserService.esAdmin(cliente)).thenReturn(false);

        CuentaActualizacionRequest req = new CuentaActualizacionRequest(EstatusCuenta.ACTIVA);
        assertThrows(AccessDeniedException.class, () -> servicio.revisarCuenta("1234567890", req));
        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    @DisplayName("Activar cuenta de un cliente inactivo es rechazado")
    void revisarCuenta_activarConClienteInactivo_lanzaExcepcion() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setActivo(false);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setEstatus(EstatusCuenta.PENDIENTE_REVISION);
        cuenta.setCliente(cliente);

        when(cuentaRepository.findWithClienteByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        CuentaActualizacionRequest request = new CuentaActualizacionRequest(EstatusCuenta.ACTIVA);

        assertThrows(ReglaNegocioException.class,
                () -> servicio.revisarCuenta("1234567890", request));
        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    @DisplayName("Rechazar cuenta exige observacionRevision obligatoria y estatus previo PENDIENTE_REVISION")
    void revisarCuenta_rechazoValidaciones_lanzaReglaNegocio() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cuenta pendiente = new Cuenta();
        pendiente.setNumeroCuenta("1234567890");
        pendiente.setEstatus(EstatusCuenta.PENDIENTE_REVISION);
        pendiente.setCliente(clienteActivo(1L));
        when(cuentaRepository.findWithClienteByNumeroCuenta("1234567890")).thenReturn(Optional.of(pendiente));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.revisarCuenta("1234567890", new CuentaActualizacionRequest(EstatusCuenta.RECHAZADA, "  ")));

        pendiente.setEstatus(EstatusCuenta.ACTIVA);
        assertThrows(ReglaNegocioException.class,
                () -> servicio.revisarCuenta("1234567890", new CuentaActualizacionRequest(EstatusCuenta.RECHAZADA, "Motivo")));
    }

    @Test
    @DisplayName("Cancelar cuenta con transacciones en proceso o pendientes de conciliacion lanza ReglaNegocioException")
    void revisarCuenta_cancelarConTxPendientes_lanzaReglaNegocio() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cuenta activa = new Cuenta();
        activa.setId(5L);
        activa.setNumeroCuenta("1234567890");
        activa.setEstatus(EstatusCuenta.ACTIVA);
        activa.setCliente(clienteActivo(1L));
        when(cuentaRepository.findWithClienteByNumeroCuenta("1234567890")).thenReturn(Optional.of(activa));
        when(transaccionServicioRepository.existsByCuentaIdAndEstatusIn(any(), any())).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> servicio.revisarCuenta("1234567890", new CuentaActualizacionRequest(EstatusCuenta.CANCELADA, "Baja")));
        verify(cuentaRepository, never()).save(any(Cuenta.class));
    }

    @Test
    @DisplayName("No se puede regresar una cuenta a estatus PENDIENTE_REVISION manualmente")
    void revisarCuenta_regresarAPendiente_lanzaReglaNegocio() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        CuentaActualizacionRequest request = new CuentaActualizacionRequest(EstatusCuenta.PENDIENTE_REVISION);
        assertThrows(ReglaNegocioException.class, () -> servicio.revisarCuenta("1234567890", request));
    }

    @Test
    @DisplayName("ADMIN puede consultar la bandeja de cuentas pendientes de revision")
    void obtenerPendientesRevision_adminListaPendientes() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cuenta pendiente = new Cuenta();
        pendiente.setNumeroCuenta("1234567890");
        pendiente.setEstatus(EstatusCuenta.PENDIENTE_REVISION);
        when(cuentaRepository.findByEstatusOrderByFechaCreacionAsc(EstatusCuenta.PENDIENTE_REVISION))
                .thenReturn(List.of(pendiente));
        when(cuentaMapper.toResponses(List.of(pendiente))).thenReturn(List.of(new CuentaResponse()));

        List<CuentaResponse> resultado = servicio.obtenerPendientesRevision();
        assertEquals(1, resultado.size());
    }

    @Test
    @DisplayName("Cliente solo puede consultar el saldo de su propia cuenta de ahorro")
    void consultarSaldo_propiaCuenta_devuelveSaldo() {
        Usuario cliente = usuarioCliente(1L);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(cliente);
        when(authenticatedUserService.esAdmin(cliente)).thenReturn(false);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setSaldo(new BigDecimal("2500.50"));
        cuenta.setCliente(clienteActivo(1L));
        when(cuentaRepository.findWithClienteByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        BigDecimal saldo = servicio.consultarSaldo("1234567890");

        assertEquals(new BigDecimal("2500.50"), saldo);
    }

    @Test
    @DisplayName("Cliente que intenta consultar saldo ajeno recibe AccessDeniedException")
    void consultarSaldo_cuentaAjena_lanzaAccessDenied() {
        Usuario cliente = usuarioCliente(1L);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(cliente);
        when(authenticatedUserService.esAdmin(cliente)).thenReturn(false);

        Cuenta cuentaAjena = new Cuenta();
        cuentaAjena.setNumeroCuenta("9999999999");
        cuentaAjena.setCliente(clienteActivo(99L));
        when(cuentaRepository.findWithClienteByNumeroCuenta("9999999999")).thenReturn(Optional.of(cuentaAjena));

        assertThrows(AccessDeniedException.class, () -> servicio.consultarSaldo("9999999999"));
    }

    @Test
    @DisplayName("Consultar saldo de cuenta inexistente lanza ResourceNotFoundException")
    void consultarSaldo_inexistente_lanzaExcepcion() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(cuentaRepository.findWithClienteByNumeroCuenta("0000000000")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> servicio.consultarSaldo("0000000000"));
    }

    private static Cliente clienteActivo(Long id) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setActivo(true);
        return cliente;
    }

    private static Usuario usuarioAdmin() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setCorreo("admin@banco.com");
        u.setRol(AuthenticatedUserService.ROLE_ADMIN);
        u.setActivo(true);
        return u;
    }

    private static Usuario usuarioCliente(Long clienteId) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setCorreo("cliente@banco.com");
        u.setRol(AuthenticatedUserService.ROLE_CLIENTE);
        u.setActivo(true);
        u.setCliente(clienteActivo(clienteId));
        return u;
    }
}
