package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.SesionActivaExistenteException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.model.seguridad.RefreshTokenRequest;
import com.proyecto.servicios.model.seguridad.SesionActivaResponse;
import com.proyecto.servicios.model.seguridad.TotpRecoveryRequest;
import com.proyecto.servicios.model.seguridad.TotpVerificationRequest;
import com.proyecto.servicios.security.CustomUserDetails;
import com.proyecto.servicios.security.JwtAccessDeniedHandler;
import com.proyecto.servicios.security.JwtAuthenticationEntryPoint;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.security.SecurityConfig;
import com.proyecto.servicios.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        JwtAuthenticationFilter.class
})
@DisplayName("Pruebas de Integracion Web y Seguridad de AuthController")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthController authController;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /auth/login publico retorna challenge TOTP sin JWT, ignora X-Forwarded-For por defecto y lo respeta si trustForwardedHeaders=true")
    void login_publico_retornaChallengeYExtraeMetadata() throws Exception {
        AuthResponse challengeResp = new AuthResponse();
        challengeResp.setCodigo(0);
        challengeResp.setMensaje("Configuracion de Google Authenticator requerida");
        challengeResp.setTipoToken(null);
        challengeResp.setCorreo("cliente@banco.com");
        challengeResp.setRol("ROLE_CLIENTE");
        challengeResp.setRequiereTotp(true);
        challengeResp.setTotpConfiguracionRequerida(true);
        challengeResp.setChallengeToken("chal-uuid-123");
        challengeResp.setTotpSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        challengeResp.setOtpauthUri("otpauth://totp/SistemaBancario:cliente%40banco.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

        when(authService.login(any(LoginRequest.class), any(ClientMetadata.class))).thenReturn(challengeResp);

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "198.51.100.25, 10.0.0.1")
                        .header(HttpHeaders.USER_AGENT, "BancaMovil/2.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cliente@banco.com", "Banco123@"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereTotp").value(true))
                .andExpect(jsonPath("$.totpConfiguracionRequerida").value(true))
                .andExpect(jsonPath("$.challengeToken").value("chal-uuid-123"))
                .andExpect(jsonPath("$.totpSecret").value("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        ArgumentCaptor<ClientMetadata> metadataCaptor = ArgumentCaptor.forClass(ClientMetadata.class);
        verify(authService).login(any(LoginRequest.class), metadataCaptor.capture());
        assertEquals("127.0.0.1", metadataCaptor.getValue().ipOrigen());
        assertEquals("BancaMovil/2.0", metadataCaptor.getValue().userAgent());

        ReflectionTestUtils.setField(authController, "trustForwardedHeaders", true);
        try {
            mockMvc.perform(post("/auth/login")
                            .header("X-Forwarded-For", "198.51.100.25, 10.0.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("cliente@banco.com", "Banco123@"))))
                    .andExpect(status().isOk());

            ArgumentCaptor<ClientMetadata> trustedCaptor = ArgumentCaptor.forClass(ClientMetadata.class);
            verify(authService, org.mockito.Mockito.times(2))
                    .login(any(LoginRequest.class), trustedCaptor.capture());
            assertEquals("198.51.100.25", trustedCaptor.getValue().ipOrigen());
        } finally {
            ReflectionTestUtils.setField(authController, "trustForwardedHeaders", false);
        }
    }

    @Test
    @DisplayName("POST /auth/login valida formato de correo y codigoTotp de 6 digitos (400 Bad Request)")
    void login_dtoInvalido_retorna400() throws Exception {
        LoginRequest invalido = new LoginRequest("correo-invalido", "Banco123@", "12ab");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /auth/login retorna 401 ante BadCredentialsException y 403 ante DisabledException")
    void login_erroresAutenticacion_retornan401Y403() throws Exception {
        when(authService.login(any(LoginRequest.class), any(ClientMetadata.class)))
                .thenThrow(new BadCredentialsException("Credenciales invalidas"))
                .thenThrow(new DisabledException("Usuario inactivo"));

        String payload = objectMapper.writeValueAsString(new LoginRequest("cliente@banco.com", "Banco123@"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(401));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    @DisplayName("POST /auth/totp/verificar publico emite tokens cuando es valido y rechaza formato invalido (400)")
    void verificarTotp_publico_validoEInvalido() throws Exception {
        AuthResponse okResp = new AuthResponse("jwt-ok", "cliente@banco.com", "ROLE_CLIENTE", 1L, "Juan Garcia");
        okResp.setRefreshToken("refresh-ok-12345678901234567890");
        okResp.setExpiresInSegundos(900L);
        okResp.setRequiereTotp(false);
        okResp.setTotpConfiguracionRequerida(false);

        when(authService.verificarTotp(any(TotpVerificationRequest.class), any(ClientMetadata.class)))
                .thenReturn(okResp);

        mockMvc.perform(post("/auth/totp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpVerificationRequest("chal-123", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-ok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-ok-12345678901234567890"))
                .andExpect(jsonPath("$.expiresInSegundos").value(900))
                .andExpect(jsonPath("$.requiereTotp").value(false));

        mockMvc.perform(post("/auth/totp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpVerificationRequest("chal-123", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    @DisplayName("POST /auth/refresh publico rota tokens y rechaza token demasiado corto (400)")
    void refresh_publico_validoEInvalido() throws Exception {
        AuthResponse refreshed = new AuthResponse("jwt-new", "cliente@banco.com", "ROLE_CLIENTE", 1L, "Juan Garcia");
        refreshed.setRefreshToken("refresh-new-12345678901234567890");
        refreshed.setExpiresInSegundos(900L);
        refreshed.setRequiereTotp(false);

        when(authService.refresh(any(RefreshTokenRequest.class), any(ClientMetadata.class)))
                .thenReturn(refreshed);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest("refresh-old-12345678901234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-new"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-new-12345678901234567890"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("corto"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    @DisplayName("POST /auth/logout acepta peticion con Bearer/body y peticion vacia")
    void logout_conYSinBody_retorna200() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer logout-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshTokenRequest("refresh-logout-123456789012345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));

        verify(authService).logout(eq("Bearer logout-jwt"), any(RefreshTokenRequest.class), any(ClientMetadata.class));

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));

        verify(authService).logout(isNull(), isNull(), any(ClientMetadata.class));
    }

    @Test
    @DisplayName("POST /auth/logout-all bloquea acceso anonimo y permite acceso con JWT valido")
    void logoutAll_seguridad_rechazaAnonimoYPermiteJwtValido() throws Exception {
        mockMvc.perform(post("/auth/logout-all"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);

        Usuario usuario = crearUsuario("cliente@banco.com", "ROLE_CLIENTE");
        CustomUserDetails userDetails = new CustomUserDetails(usuario);
        when(jwtService.extractUsername("jwt-valido")).thenReturn("cliente@banco.com");
        when(userDetailsService.loadUserByUsername("cliente@banco.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("jwt-valido", userDetails)).thenReturn(true);

        mockMvc.perform(post("/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));

        verify(authService).logoutAll(any(ClientMetadata.class));
    }

    @Test
    @DisplayName("GET /auth/auditoria bloquea anonimo y ROLE_CLIENTE (403), y permite ROLE_ADMIN (200)")
    @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
    void auditoria_clienteBloqueado403() throws Exception {
        mockMvc.perform(get("/auth/auditoria"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /auth/auditoria sin autenticacion es rechazado")
    void auditoria_anonimoRechazado() throws Exception {
        mockMvc.perform(get("/auth/auditoria"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /auth/auditoria con ROLE_ADMIN retorna pagina de auditoria y respeta filtros")
    @WithMockUser(username = "admin@banco.com", roles = "ADMIN")
    void auditoria_adminRetorna200() throws Exception {
        AuditoriaLoginResponse item = AuditoriaLoginResponse.builder()
                .id(1L)
                .usuarioId(10L)
                .correo("cliente@banco.com")
                .evento("EXITO")
                .exitoso(true)
                .ipOrigen("198.51.100.25")
                .fechaCreacion(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        PageResponse<AuditoriaLoginResponse> pageResp = PageResponse.<AuditoriaLoginResponse>builder()
                .contenido(List.of(item))
                .paginaActual(1)
                .tamanoPagina(10)
                .totalElementos(1L)
                .totalPaginas(1)
                .primera(false)
                .ultima(true)
                .build();

        when(authService.consultarAuditoria("cliente@banco.com", PageRequest.of(1, 10)))
                .thenReturn(pageResp);

        mockMvc.perform(get("/auth/auditoria")
                        .param("correo", "cliente@banco.com")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].evento").value("EXITO"))
                .andExpect(jsonPath("$.contenido[0].correo").value("cliente@banco.com"))
                .andExpect(jsonPath("$.paginaActual").value(1))
                .andExpect(jsonPath("$.tamanoPagina").value(10));

        verify(authService).consultarAuditoria("cliente@banco.com", PageRequest.of(1, 10));
    }

    @Test
    @DisplayName("GET /auth/auditoria valida page >= 0 y size entre 1 y 100 antes de paginar (400 Bad Request)")
    @WithMockUser(username = "admin@banco.com", roles = "ADMIN")
    void auditoria_paginacionInvalida_retorna400() throws Exception {
        mockMvc.perform(get("/auth/auditoria").param("page", "-1").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));

        mockMvc.perform(get("/auth/auditoria").param("page", "0").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /auth/login retorna 409 Conflict con SESION_ACTIVA_EXISTENTE cuando hay sesion activa previa")
    void login_sesionActivaExistente_retorna409() throws Exception {
        when(authService.login(any(LoginRequest.class), any(ClientMetadata.class)))
                .thenThrow(new SesionActivaExistenteException(
                        "Ya existe una sesion activa en otro dispositivo.",
                        "198.51.100.8",
                        "FlutterApp/1.0",
                        LocalDateTime.of(2026, 11, 26, 12, 0)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cliente@banco.com", "Banco123@"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409))
                .andExpect(jsonPath("$.errores.codigoError").value("SESION_ACTIVA_EXISTENTE"))
                .andExpect(jsonPath("$.errores.sesionActivaExistente").value("true"))
                .andExpect(jsonPath("$.errores.dispositivo").value("FlutterApp/1.0"));
    }

    @Test
    @DisplayName("POST /auth/totp/recuperar es publico, retorna nuevo enrollment y rechaza formato invalido (400)")
    void recuperarAccesoTotp_publico_retorna200O400() throws Exception {
        AuthResponse enrollment = new AuthResponse();
        enrollment.setCodigo(0);
        enrollment.setMensaje("Configuracion de Google Authenticator requerida");
        enrollment.setRequiereTotp(true);
        enrollment.setTotpConfiguracionRequerida(true);
        enrollment.setChallengeToken("chal-rec-1");
        enrollment.setTotpSecret("NEWBASE32SECRET");
        when(authService.recuperarAccesoTotp(any(TotpRecoveryRequest.class), any(ClientMetadata.class)))
                .thenReturn(enrollment);

        mockMvc.perform(post("/auth/totp/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TotpRecoveryRequest("cliente@banco.com", "Banco123@", "ABCD-2345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereTotp").value(true))
                .andExpect(jsonPath("$.totpConfiguracionRequerida").value(true))
                .andExpect(jsonPath("$.challengeToken").value("chal-rec-1"));

        mockMvc.perform(post("/auth/totp/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TotpRecoveryRequest("cliente@banco.com", "Banco123@", "malo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    @DisplayName("GET /auth/sesion-activa requiere autenticacion (401) y retorna sesion vigente si autenticado")
    void consultarSesionActiva_seguridadYRespuesta() throws Exception {
        mockMvc.perform(get("/auth/sesion-activa"))
                .andExpect(status().isUnauthorized());

        when(authService.consultarSesionActiva()).thenReturn(SesionActivaResponse.builder()
                .activa(true)
                .correo("cliente@banco.com")
                .ipOrigen("198.51.100.8")
                .userAgent("WebApp/1.0")
                .tokenVersion(2L)
                .build());

        Usuario usuario = crearUsuario("cliente@banco.com", "ROLE_CLIENTE");
        CustomUserDetails userDetails = new CustomUserDetails(usuario);
        when(jwtService.extractUsername("jwt-valido")).thenReturn("cliente@banco.com");
        when(userDetailsService.loadUserByUsername("cliente@banco.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("jwt-valido", userDetails)).thenReturn(true);

        mockMvc.perform(get("/auth/sesion-activa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.correo").value("cliente@banco.com"))
                .andExpect(jsonPath("$.userAgent").value("WebApp/1.0"));
    }

    private Usuario crearUsuario(String correo, String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCorreo(correo);
        usuario.setPasswordHash("$2a$10$hash");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setTokenVersion(0L);
        return usuario;
    }
}
