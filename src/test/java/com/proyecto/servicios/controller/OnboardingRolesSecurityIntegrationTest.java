package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.cuentas.TipoCuenta;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.RateLimitExceededException;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.model.seguridad.UsuarioCreacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioResponse;
import com.proyecto.servicios.security.JwtAccessDeniedHandler;
import com.proyecto.servicios.security.JwtAuthenticationEntryPoint;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.security.PublicEndpointRateLimiter;
import com.proyecto.servicios.security.SecurityConfig;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ClienteController.class, CuentaController.class, UsuarioController.class})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        JwtAuthenticationFilter.class
})
@DisplayName("Pruebas de Seguridad por Roles (Onboarding, Cuenta Unica de Ahorro y Revision Admin)")
class OnboardingRolesSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ClienteService clienteService;
    @MockBean private CuentaService cuentaService;
    @MockBean private UsuarioService usuarioService;
    @MockBean private PublicEndpointRateLimiter publicEndpointRateLimiter;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("ROLE_CLIENTE NO puede listar clientes ni cuentas pendientes de onboarding (403 Forbidden)")
    void cliente_bloqueadoEnEndpointsAdmin_retorna403() throws Exception {
        mockMvc.perform(get("/clientes")).andExpect(status().isForbidden());
        mockMvc.perform(get("/cuentas")).andExpect(status().isForbidden());
        mockMvc.perform(get("/cuentas/pendientes")).andExpect(status().isForbidden());
        mockMvc.perform(put("/cuentas/1234567890/revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CuentaActualizacionRequest(EstatusCuenta.ACTIVA))))
                .andExpect(status().isForbidden());

        UsuarioCreacionRequest reqAdmin = new UsuarioCreacionRequest(null, "escalar@banco.com", "Admin123@", "ROLE_ADMIN");
        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@banco.com", roles = "ADMIN")
    @DisplayName("ROLE_ADMIN puede revisar cuentas de onboarding pendientes y aprobarlas")
    void admin_revisaYAprobarCuentaOnboarding_retorna200() throws Exception {
        CuentaResponse pendiente = new CuentaResponse();
        pendiente.setNumeroCuenta("1234567890");
        pendiente.setTipoCuenta(TipoCuenta.AHORRO);
        pendiente.setEstatus(EstatusCuenta.PENDIENTE_REVISION);
        pendiente.setSaldo(new BigDecimal("500.00"));

        when(cuentaService.obtenerPendientesRevision()).thenReturn(List.of(pendiente));

        CuentaResponse aprobada = new CuentaResponse();
        aprobada.setNumeroCuenta("1234567890");
        aprobada.setTipoCuenta(TipoCuenta.AHORRO);
        aprobada.setEstatus(EstatusCuenta.ACTIVA);
        aprobada.setRevisadoPor("admin@banco.com");
        when(cuentaService.revisarCuenta(eq("1234567890"), any(CuentaActualizacionRequest.class)))
                .thenReturn(aprobada);

        mockMvc.perform(get("/cuentas/pendientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estatus").value("PENDIENTE_REVISION"))
                .andExpect(jsonPath("$[0].tipoCuenta").value("AHORRO"));

        mockMvc.perform(put("/cuentas/1234567890/revision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CuentaActualizacionRequest(EstatusCuenta.ACTIVA, "Identidad validada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estatus").value("ACTIVA"))
                .andExpect(jsonPath("$.revisadoPor").value("admin@banco.com"));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("ROLE_CLIENTE puede consultar su unica cuenta de ahorro en /cuentas/mi-cuenta")
    void cliente_consultaSuCuentaAhorro_retorna200() throws Exception {
        CuentaResponse miCuenta = new CuentaResponse();
        miCuenta.setNumeroCuenta("1234567890");
        miCuenta.setTipoCuenta(TipoCuenta.AHORRO);
        miCuenta.setEstatus(EstatusCuenta.ACTIVA);
        miCuenta.setSaldo(new BigDecimal("1000.00"));

        when(cuentaService.obtenerMiCuenta()).thenReturn(miCuenta);

        mockMvc.perform(get("/cuentas/mi-cuenta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCuenta").value("AHORRO"))
                .andExpect(jsonPath("$.numeroCuenta").value("1234567890"));
    }

    @Test
    @WithMockUser(username = "admin@banco.com", roles = "ADMIN")
    @DisplayName("ROLE_ADMIN puede crear otro usuario administrador en /usuarios")
    void admin_creaAdmin_retorna201() throws Exception {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setId(2L);
        resp.setCorreo("auditor@banco.com");
        resp.setRol("ROLE_ADMIN");
        resp.setActivo(true);
        when(usuarioService.crear(any(UsuarioCreacionRequest.class))).thenReturn(resp);

        UsuarioCreacionRequest req = new UsuarioCreacionRequest(null, "auditor@banco.com", "Admin123@", "ROLE_ADMIN");
        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("POST /clientes retorna 429 Too Many Requests con Retry-After cuando excede rate limit de onboarding")
    void registrarCliente_rateLimitExcedido_retorna429() throws Exception {
        doThrow(new RateLimitExceededException("Demasiadas solicitudes de registro", 120L))
                .when(publicEndpointRateLimiter).verificarLimiteOnboarding(anyString());

        String payload = """
                {
                  "nombre": "Juan",
                  "apellidoPaterno": "Garcia",
                  "apellidoMaterno": "Lopez",
                  "fechaNacimiento": "1995-05-10",
                  "curp": "GALJ950510HDFRRL09",
                  "rfc": "GALJ950510AB1",
                  "generoId": 1,
                  "nacionalidadId": 1,
                  "estadoCivilId": 1,
                  "correo": "juan.garcia@banco.com",
                  "telefonoMovil": "5512345678",
                  "ocupacion": "Ingeniero",
                  "empresa": "Tech SA",
                  "ingresoMensual": 25000.00,
                  "domicilio": {
                    "calle": "Reforma",
                    "noExterior": "100",
                    "cp": "06600",
                    "coloniaId": 1,
                    "municipioId": 1,
                    "estadoId": 1,
                    "paisId": 1
                  },
                  "saldoInicial": 100.00,
                  "password": "Password1@"
                }
                """;

        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.codigo").value(429));
    }

    @Test
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    @DisplayName("ROLE_CLIENTE recibe 403 al intentar POST /usuarios/{id}/reset-totp")
    void resetTotp_clienteBloqueado_retorna403() throws Exception {
        mockMvc.perform(post("/usuarios/10/reset-totp"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@banco.com", roles = "ADMIN")
    @DisplayName("ROLE_ADMIN puede ejecutar POST /usuarios/{id}/reset-totp")
    void resetTotp_adminPermitido_retorna200() throws Exception {
        UsuarioResponse resp = new UsuarioResponse();
        resp.setCodigo(0);
        resp.setId(10L);
        resp.setMensaje("TOTP restablecido");
        when(usuarioService.resetearTotp(10L)).thenReturn(resp);

        mockMvc.perform(post("/usuarios/10/reset-totp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.id").value(10));
    }
}

