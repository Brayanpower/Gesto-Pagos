package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.gestopago.TransaccionServicio;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.OperacionRechazadaProveedorException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.TransaccionTimeoutConciliacionException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult.EstadoProveedor;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.HistorialCompraResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.PagoServicioResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.CustomUserDetails;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.GestoPagoSendTxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de PagoServicioServiceImpl")
class PagoServicioServiceImplTest {

    @Mock private CatalogoService catalogoService;
    @Mock private TransaccionPersistenceService persistenceService;
    @Mock private GestoPagoSendTxService gestoPagoSendTxService;
    @Mock private TransaccionServicioRepository transaccionRepository;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PagoServicioServiceImpl servicio;

    private Usuario usuarioCliente;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(servicio, "unidadDistribuidor", "GPS83-TPV-17");
        ReflectionTestUtils.setField(servicio, "montoMinimoPermitido", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(servicio, "montoMaximoPermitido", new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(servicio, "conciliacionInmediata", true);
        ReflectionTestUtils.setField(servicio, "maxIntentosConciliacion", 5);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setActivo(true);
        cliente.setTelefonoMovil("5511223344");

        usuarioCliente = new Usuario();
        usuarioCliente.setId(1L);
        usuarioCliente.setCorreo("cliente@banco.com");
        usuarioCliente.setPasswordHash("hash");
        usuarioCliente.setRol("ROLE_CLIENTE");
        usuarioCliente.setActivo(true);
        usuarioCliente.setCliente(cliente);

        CustomUserDetails details = new CustomUserDetails(usuarioCliente);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Flujo completo exitoso: valida catalogo, descuenta saldo, envia a GestoPago y confirma EXITOSA")
    void procesarPagoServicio_exitoCompleto() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        when(catalogoService.validarYObtenerProducto(2284, 14302)).thenReturn(productoRecargaFija("100.0"));

        TransaccionServicio enProceso = transaccionMock(EstatusTransaccion.EN_PROCESO);
        when(persistenceService.reservarYDebitarSaldo(
                eq(10L), eq("1234567890"), anyString(), any(), eq(2284), eq(14302),
                eq(new BigDecimal("100.00")), eq("5512345678"), eq(null)))
                .thenReturn(enProceso);

        GestoPagoTxResult aprobado = GestoPagoTxResult.builder()
                .estado(EstadoProveedor.APROBADA)
                .codigo("01")
                .mensaje("Operacion realizada con exito")
                .idTx("TX-900")
                .numAutorizacion("AUT-777")
                .build();
        when(gestoPagoSendTxService.ejecutarTransaccion(any(SendTx.class))).thenReturn(aprobado);

        TransaccionServicio confirmada = transaccionMock(EstatusTransaccion.EXITOSA);
        confirmada.setFolioProveedor("TX-900");
        confirmada.setNumAutorizacion("AUT-777");
        when(persistenceService.confirmarExito(1L, aprobado)).thenReturn(confirmada);

        SendTxRequest req = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .build();

        PagoServicioResponse resp = servicio.procesarPagoServicio(req, null);

        assertEquals(EstatusTransaccion.EXITOSA, resp.getEstatus());
        assertEquals("TX-900", resp.getFolioProveedor());
        assertEquals("AUT-777", resp.getNumAutorizacion());
        verify(gestoPagoSendTxService, times(1)).ejecutarTransaccion(any(SendTx.class));
        verify(persistenceService, never()).reversarCargo(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Bloquea manipulacion de monto cuando no coincide con el precio del producto en catalogo")
    void procesarPagoServicio_montoManipulado_rechazaAntesDeDebitar() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        when(catalogoService.validarYObtenerProducto(2284, 14302)).thenReturn(productoRecargaFija("100.0"));

        SendTxRequest req = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("10.00"))
                .telefono("5512345678")
                .build();

        assertThrows(ReglaNegocioException.class, () -> servicio.procesarPagoServicio(req, null));
        verify(persistenceService, never()).reservarYDebitarSaldo(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(gestoPagoSendTxService, never()).ejecutarTransaccion(any());
    }

    @Test
    @DisplayName("Rechazo explicito del proveedor reversa el saldo del cliente automaticamente")
    void procesarPagoServicio_rechazoProveedor_reversaSaldo() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        when(catalogoService.validarYObtenerProducto(2284, 14302)).thenReturn(productoRecargaFija("100.0"));
        TransaccionServicio enProceso = transaccionMock(EstatusTransaccion.EN_PROCESO);
        when(persistenceService.reservarYDebitarSaldo(
                anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(enProceso);

        GestoPagoTxResult rechazado = GestoPagoTxResult.builder()
                .estado(EstadoProveedor.RECHAZADA_PROVEEDOR)
                .codigo("05")
                .mensaje("Linea suspendida")
                .build();
        when(gestoPagoSendTxService.ejecutarTransaccion(any())).thenReturn(rechazado);

        SendTxRequest req = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .build();

        assertThrows(OperacionRechazadaProveedorException.class, () -> servicio.procesarPagoServicio(req, null));
        verify(persistenceService).reversarCargo(
                eq(1L), eq(EstatusTransaccion.RECHAZADA), eq("05"), eq("Linea suspendida"));
    }

    @Test
    @DisplayName("Timeout en sendTx NO reintenta el cargo: consulta conciliacion y mantiene PENDIENTE_CONCILIACION si sigue indeterminado")
    void procesarPagoServicio_timeout_noReintentaCargoYMarcaConciliacion() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        when(catalogoService.validarYObtenerProducto(2284, 14302)).thenReturn(productoRecargaFija("100.0"));
        TransaccionServicio enProceso = transaccionMock(EstatusTransaccion.EN_PROCESO);
        when(persistenceService.reservarYDebitarSaldo(
                anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(enProceso);

        GestoPagoTxResult timeoutResult = GestoPagoTxResult.builder()
                .estado(EstadoProveedor.PENDIENTE_PROVEEDOR)
                .codigo("TIMEOUT")
                .mensaje("Read timed out")
                .build();
        when(gestoPagoSendTxService.ejecutarTransaccion(any())).thenReturn(timeoutResult);

        TransaccionServicio pendiente = transaccionMock(EstatusTransaccion.PENDIENTE_CONCILIACION);
        when(persistenceService.marcarPendienteConciliacion(eq(1L), anyString(), anyString(), anyBoolean()))
                .thenReturn(pendiente);
        when(gestoPagoSendTxService.verificarTransaccion(any())).thenReturn(timeoutResult);

        SendTxRequest req = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .build();

        TransaccionTimeoutConciliacionException ex = assertThrows(
                TransaccionTimeoutConciliacionException.class,
                () -> servicio.procesarPagoServicio(req, null)
        );

        assertEquals("9988776655", ex.getUpc());
        assertEquals(EstatusTransaccion.PENDIENTE_CONCILIACION, ex.getEstatus());
        // Verifica que sendTx solo se invoco UNA vez (sin reintentar el cobro) y NO se reverso a ciegas
        verify(gestoPagoSendTxService, times(1)).ejecutarTransaccion(any());
        verify(gestoPagoSendTxService, times(1)).verificarTransaccion(any());
        verify(persistenceService, never()).reversarCargo(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Idempotency-Key repetida retorna la operacion exitosa previa sin volver a cobrar ni llamar al proveedor")
    void procesarPagoServicio_claveIdempotenteRepetida_noCobraDoble() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        TransaccionServicio previa = transaccionMock(EstatusTransaccion.EXITOSA);
        previa.setCliente(cliente);
        previa.setFolioProveedor("TX-111");
        when(persistenceService.buscarPorClaveIdempotencia("idem-unica-001")).thenReturn(Optional.of(previa));

        SendTxRequest req = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .build();

        PagoServicioResponse resp = servicio.procesarPagoServicio(req, "idem-unica-001");

        assertTrue(resp.getRespuestaIdempotente());
        assertEquals("TX-111", resp.getFolioProveedor());
        verify(persistenceService, never()).reservarYDebitarSaldo(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(gestoPagoSendTxService, never()).ejecutarTransaccion(any());
    }

    @Test
    @DisplayName("Idempotency-Key reutilizada con monto distinto lanza DuplicateResourceException (409)")
    void procesarPagoServicio_idempotencyKeyParametrosDistintos_lanzaConflicto() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        TransaccionServicio previa = transaccionMock(EstatusTransaccion.EXITOSA);
        previa.setCliente(cliente);
        when(persistenceService.buscarPorClaveIdempotencia("idem-unica-001")).thenReturn(Optional.of(previa));

        SendTxRequest reqManipulado = SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("200.00"))
                .telefono("5512345678")
                .build();

        assertThrows(DuplicateResourceException.class,
                () -> servicio.procesarPagoServicio(reqManipulado, "idem-unica-001"));
    }

    @Test
    @DisplayName("Conciliacion confirma rechazo del proveedor y restituye el saldo (REVERSADA)")
    void conciliarTransaccion_proveedorConfirmaNoAplicada_reversaSaldo() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        TransaccionServicio pendiente = transaccionMock(EstatusTransaccion.PENDIENTE_CONCILIACION);
        pendiente.setCliente(cliente);
        when(transaccionRepository.findByUpc("9988776655")).thenReturn(Optional.of(pendiente));

        GestoPagoTxResult noExiste = GestoPagoTxResult.builder()
                .estado(EstadoProveedor.RECHAZADA_PROVEEDOR)
                .codigo("14")
                .mensaje("Transaccion no registrada en proveedor")
                .build();
        when(gestoPagoSendTxService.verificarTransaccion(any())).thenReturn(noExiste);

        TransaccionServicio reversada = transaccionMock(EstatusTransaccion.REVERSADA);
        reversada.setSaldoPosterior(new BigDecimal("500.00"));
        when(persistenceService.reversarCargo(eq(1L), eq(EstatusTransaccion.REVERSADA), eq("14"), anyString()))
                .thenReturn(reversada);

        PagoServicioResponse resp = servicio.conciliarTransaccion("9988776655");

        assertEquals(EstatusTransaccion.REVERSADA, resp.getEstatus());
        assertEquals(new BigDecimal("500.00"), resp.getSaldoDisponible());
    }

    @Test
    @DisplayName("Historial paginado con filtro de fechas devuelve PageResponse correctamente")
    @SuppressWarnings("unchecked")
    void obtenerHistorialCompras_paginadoConFechas_exito() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));
        TransaccionServicio tx = transaccionMock(EstatusTransaccion.EXITOSA);
        tx.setFechaCreacion(LocalDateTime.of(2026, 10, 15, 14, 30));

        when(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        PageResponse<HistorialCompraResponse> page = servicio.obtenerHistorialCompras(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                null,
                EstatusTransaccion.EXITOSA,
                0,
                10,
                "fechaCreacion",
                "DESC"
        );

        assertNotNull(page);
        assertEquals(1, page.getTotalElementos());
        assertEquals("9988776655", page.getContenido().get(0).getUpc());
    }

    @Test
    @DisplayName("Historial rechaza rango de fechas invertido y acceso a cuenta de otro cliente")
    void obtenerHistorialCompras_validacionesFechasYTitularidad() {
        when(usuarioRepository.findWithClienteByCorreo("cliente@banco.com")).thenReturn(Optional.of(usuarioCliente));

        assertThrows(ReglaNegocioException.class, () -> servicio.obtenerHistorialCompras(
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 10, 1),
                null, null, 0, 10, "fechaCreacion", "DESC"
        ));

        Cliente otro = new Cliente();
        otro.setId(999L);
        Cuenta cuentaAjena = new Cuenta();
        cuentaAjena.setNumeroCuenta("9999999999");
        cuentaAjena.setEstatus(EstatusCuenta.ACTIVA);
        cuentaAjena.setCliente(otro);
        when(cuentaRepository.findWithClienteByNumeroCuenta("9999999999")).thenReturn(Optional.of(cuentaAjena));

        assertThrows(AccessDeniedException.class, () -> servicio.obtenerHistorialCompras(
                null, null, "9999999999", null, 0, 10, "fechaCreacion", "DESC"
        ));
    }

    private static CatalogoProducto productoRecargaFija(String precio) {
        CatalogoProducto prod = new CatalogoProducto();
        prod.setIdServicio("2284");
        prod.setIdProducto("14302");
        prod.setServicio("ABIB");
        prod.setNombreProducto("ABIB 100");
        prod.setTipoFront("1");
        prod.setPrecio(precio);
        return prod;
    }

    private static TransaccionServicio transaccionMock(EstatusTransaccion estatus) {
        TransaccionServicio tx = new TransaccionServicio();
        tx.setId(1L);
        tx.setNumeroCuenta("1234567890");
        tx.setUpc("9988776655");
        tx.setIdServicio(2284);
        tx.setNombreServicio("ABIB");
        tx.setIdProducto(14302);
        tx.setNombreProducto("ABIB 100");
        tx.setMonto(new BigDecimal("100.00"));
        tx.setSaldoAnterior(new BigDecimal("500.00"));
        tx.setSaldoPosterior(new BigDecimal("400.00"));
        tx.setTelefono("5512345678");
        tx.setHoraLocal("20261030093000");
        tx.setEstatus(estatus);
        tx.setFechaCreacion(LocalDateTime.now());
        return tx;
    }
}

