package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.SaldoInsuficienteException;
import com.proyecto.servicios.exception.TransaccionTimeoutConciliacionException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.HistorialCompraResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.PagoServicioResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;
import com.proyecto.servicios.security.JwtAccessDeniedHandler;
import com.proyecto.servicios.security.JwtAuthenticationEntryPoint;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.security.SecurityConfig;
import com.proyecto.servicios.service.PagoServicioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PagoServicioController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        JwtAuthenticationFilter.class
})
@DisplayName("Pruebas de Integración Web y Seguridad de PagoServicioController")
class PagoServicioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PagoServicioService pagoServicioService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /servicios/pagos sin autenticacion devuelve 401 Unauthorized")
    void realizarPago_sinToken_retorna401() throws Exception {
        SendTxRequest req = requestValido();
        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos autenticado y exitoso devuelve 201 Created")
    void realizarPago_autenticadoExitoso_retorna201() throws Exception {
        PagoServicioResponse resp = PagoServicioResponse.builder()
                .transaccionId(15L)
                .upc("1234567890")
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .nombreServicio("ABIB")
                .idProducto(14302)
                .nombreProducto("ABIB 100")
                .monto(new BigDecimal("100.00"))
                .saldoDisponible(new BigDecimal("900.00"))
                .estatus(EstatusTransaccion.EXITOSA)
                .numAutorizacion("AUT-999")
                .fechaOperacion(LocalDateTime.now())
                .build();

        when(pagoServicioService.procesarPagoServicio(any(SendTxRequest.class), eq("idem-key-00001")))
                .thenReturn(resp);

        mockMvc.perform(post("/servicios/pagos")
                        .header("Idempotency-Key", "idem-key-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estatus").value("EXITOSA"))
                .andExpect(jsonPath("$.upc").value("1234567890"))
                .andExpect(jsonPath("$.saldoDisponible").value(900.00));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos con parametros invalidos devuelve 400 Bad Request")
    void realizarPago_parametrosInvalidos_retorna400() throws Exception {
        SendTxRequest invalido = SendTxRequest.builder()
                .numeroCuenta("")
                .montoPago(new BigDecimal("-50.00"))
                .build();

        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.errores.numeroCuenta").exists())
                .andExpect(jsonPath("$.errores.idServicio").exists())
                .andExpect(jsonPath("$.errores.montoPago").exists());
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos sobre cuenta ajena devuelve 403 Forbidden")
    void realizarPago_cuentaAjena_retorna403() throws Exception {
        when(pagoServicioService.procesarPagoServicio(any(), any()))
                .thenThrow(new AccessDeniedException("No autorizado"));

        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos con solicitud duplicada devuelve 409 Conflict")
    void realizarPago_duplicado_retorna409() throws Exception {
        when(pagoServicioService.procesarPagoServicio(any(), any()))
                .thenThrow(new DuplicateResourceException("Solicitud duplicada"));

        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos con saldo insuficiente devuelve 422 Unprocessable Entity")
    void realizarPago_saldoInsuficiente_retorna422() throws Exception {
        when(pagoServicioService.procesarPagoServicio(any(), any()))
                .thenThrow(new SaldoInsuficienteException("Saldo insuficiente"));

        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(422));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("POST /servicios/pagos ante timeout del proveedor devuelve 504 con folio de conciliacion")
    void realizarPago_timeoutProveedor_retorna504ConUpcConciliacion() throws Exception {
        when(pagoServicioService.procesarPagoServicio(any(), any()))
                .thenThrow(new TransaccionTimeoutConciliacionException(
                        "7788990011",
                        "Tiempo de espera agotado; operacion en conciliacion."
                ));

        mockMvc.perform(post("/servicios/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.codigo").value(504))
                .andExpect(jsonPath("$.errores.upc").value("7788990011"))
                .andExpect(jsonPath("$.errores.estatus").value("PENDIENTE_CONCILIACION"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("GET /servicios/historial paginado y filtrado por fechas devuelve 200 OK")
    void obtenerHistorial_paginadoConFechas_retorna200() throws Exception {
        HistorialCompraResponse item = HistorialCompraResponse.builder()
                .id(1L)
                .upc("1234567890")
                .numeroCuenta("1234567890")
                .nombreServicio("TELCEL")
                .nombreProducto("Recarga 100")
                .monto(new BigDecimal("100.00"))
                .estatus(EstatusTransaccion.EXITOSA)
                .fechaOperacion(LocalDateTime.of(2026, 10, 20, 12, 0))
                .build();

        PageResponse<HistorialCompraResponse> page = PageResponse.<HistorialCompraResponse>builder()
                .contenido(List.of(item))
                .paginaActual(0)
                .tamanoPagina(5)
                .totalElementos(1)
                .totalPaginas(1)
                .primera(true)
                .ultima(true)
                .build();

        when(pagoServicioService.obtenerHistorialCompras(
                eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 31)),
                eq("1234567890"),
                eq(EstatusTransaccion.EXITOSA),
                eq(0),
                eq(5),
                eq("fechaCreacion"),
                eq("DESC")
        )).thenReturn(page);

        mockMvc.perform(get("/servicios/historial")
                        .param("fechaInicio", "2026-10-01")
                        .param("fechaFin", "2026-10-31")
                        .param("numeroCuenta", "1234567890")
                        .param("estatus", "EXITOSA")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.paginaActual").value(0))
                .andExpect(jsonPath("$.tamanoPagina").value(5))
                .andExpect(jsonPath("$.contenido[0].upc").value("1234567890"))
                .andExpect(jsonPath("$.contenido[0].estatus").value("EXITOSA"));
    }

    private static SendTxRequest requestValido() {
        return SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .build();
    }
}

