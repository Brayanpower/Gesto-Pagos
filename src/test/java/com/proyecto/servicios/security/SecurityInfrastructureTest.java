package com.proyecto.servicios.security;

import com.proyecto.servicios.controller.PagoServicioController;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.RateLimitExceededException;
import com.proyecto.servicios.service.PagoServicioService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.proyecto.servicios.model.GenericResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Pruebas de infraestructura HTTP, Filtro JWT, CORS, Headers y GlobalExceptionHandler")
class SecurityInfrastructureTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JwtAuthenticationFilter autentica solo si enabled, nonLocked y token valido")
    void jwtAuthenticationFilter_validaEstadoYToken() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, uds);

        Usuario activo = new Usuario();
        activo.setCorreo("user@banco.com");
        activo.setPasswordHash("hash");
        activo.setRol("ROLE_CLIENTE");
        activo.setActivo(true);
        CustomUserDetails detailsActivo = new CustomUserDetails(activo);

        when(jwtService.extractUsername("token-valido")).thenReturn("user@banco.com");
        when(uds.loadUserByUsername("user@banco.com")).thenReturn(detailsActivo);
        when(jwtService.isTokenValid("token-valido", detailsActivo)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-valido");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@banco.com", SecurityContextHolder.getContext().getAuthentication().getName());

        SecurityContextHolder.clearContext();

        // Usuario inactivo no debe autenticarse aunque el JWT sea valido
        activo.setActivo(false);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Error al parsear token no rompe la cadena ni autentica
        when(jwtService.extractUsername("token-expirado"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expirado Bearer secret"));
        MockHttpServletRequest reqExpirado = new MockHttpServletRequest();
        reqExpirado.addHeader("Authorization", "Bearer token-expirado");
        filter.doFilter(reqExpirado, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("GlobalExceptionHandler retorna ResponseEntity 423 y 429 con Retry-After")
    void globalExceptionHandler_retorna423Y429ConRetryAfter() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<GenericResponse> locked = handler.handleCuentaBloqueada(
                new CuentaBloqueadaException("Cuenta bloqueada temporalmente"));
        assertEquals(HttpStatus.LOCKED, locked.getStatusCode());
        assertNotNull(locked.getBody());
        assertEquals(423, locked.getBody().getCodigo());
        assertEquals("Cuenta bloqueada temporalmente", locked.getBody().getMensaje());

        ResponseEntity<GenericResponse> rateLimited = handler.handleRateLimitExceeded(
                new RateLimitExceededException("Demasiados intentos", 42L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimited.getStatusCode());
        assertEquals("42", rateLimited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(rateLimited.getBody());
        assertEquals(429, rateLimited.getBody().getCodigo());
        assertEquals("Demasiados intentos", rateLimited.getBody().getMensaje());
    }

    @Test
    @DisplayName("SecurityConfig parsea origenes CORS separados por coma sin usar comodin '*'")
    void securityConfig_corsOriginsSinComodin() {
        SecurityConfig config = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(UserDetailsService.class),
                new JwtAuthenticationEntryPoint(),
                new JwtAccessDeniedHandler());
        ReflectionTestUtils.setField(config, "allowedOrigins", " https://app.banco.com , http://localhost:3000 ");

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/auth/login"));
        assertNotNull(cors);
        assertEquals(List.of("https://app.banco.com", "http://localhost:3000"), cors.getAllowedOrigins());
        assertFalse(cors.getAllowedOrigins().contains("*"));
    }

    @Nested
    @WebMvcTest(controllers = PagoServicioController.class)
    @Import({
            SecurityConfig.class,
            GlobalExceptionHandler.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            JwtAuthenticationFilter.class
    })
    @DisplayName("SecurityConfig restringe rutas /auth y emite headers HTTP seguros")
    class WebSecurityFilterChainTests {

        @Autowired private MockMvc mockMvc;
        @MockBean private PagoServicioService pagoServicioService;
        @MockBean private JwtService jwtService;
        @MockBean private UserDetailsService userDetailsService;

        @Test
        @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
        @DisplayName("Respuestas incluyen X-Frame-Options DENY, Referrer-Policy, X-Content-Type-Options y Cache-Control")
        void respuestas_incluyenHeadersSeguros() throws Exception {
            mockMvc.perform(get("/servicios/historial"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().exists("Cache-Control"));
        }

        @Test
        @WithMockUser(username = "cliente@banco.com", roles = "CLIENTE")
        @DisplayName("GlobalExceptionHandler via MockMvc responde 423 y 429 con Retry-After")
        void globalExceptionHandler_mockMvc_responde423Y429() throws Exception {
            when(pagoServicioService.obtenerHistorialCompras(any(), any(), any(), any(), any()))
                    .thenThrow(new CuentaBloqueadaException("Cuenta bloqueada temporalmente"));

            mockMvc.perform(get("/servicios/historial"))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.codigo").value(423))
                    .andExpect(jsonPath("$.mensaje").value("Cuenta bloqueada temporalmente"));

            doThrow(new RateLimitExceededException("Demasiados intentos", 42L))
                    .when(pagoServicioService).obtenerHistorialCompras(any(), any(), any(), any(), any());

            mockMvc.perform(get("/servicios/historial"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                    .andExpect(jsonPath("$.codigo").value(429))
                    .andExpect(jsonPath("$.mensaje").value("Demasiados intentos"));
        }

        @Test
        @DisplayName("/auth/logout-all y /auth/auditoria requieren autenticacion (401 sin JWT) y endpoints publicos pasan filtro")
        void rutasProtegidasAuth_sinToken_retornan401() throws Exception {
            mockMvc.perform(post("/auth/logout-all")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/auth/auditoria")).andExpect(status().isUnauthorized());

            for (String rutaPublica : List.of("/auth/login", "/auth/totp/verificar", "/auth/refresh", "/auth/logout")) {
                int status = mockMvc.perform(post(rutaPublica)).andReturn().getResponse().getStatus();
                assertNotEquals(401, status, "La ruta publica " + rutaPublica + " no debe requerir autenticacion");
                assertNotEquals(403, status, "La ruta publica " + rutaPublica + " no debe devolver 403");
            }
        }
    }
}
