package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.model.seguridad.RefreshTokenRequest;
import com.proyecto.servicios.model.seguridad.TotpVerificationRequest;
import com.proyecto.servicios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(
        name = "Autenticación & Seguridad",
        description = "Endpoints para inicio de sesión, verificación MFA TOTP, refresh tokens, cierre de sesión y auditoría"
)
public class AuthController {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Inicio de sesión",
            description = "Autentica al usuario mediante correo y contrasena, iniciando desafio MFA TOTP o emitiendo tokens si procede"
    )
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, extraerMetadata(httpRequest)));
    }

    @PostMapping(value = "/totp/verificar", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Verificar código TOTP",
            description = "Completa el enrolamiento o verificación MFA TOTP usando challengeToken y código de 6 dígitos"
    )
    public ResponseEntity<AuthResponse> verificarTotp(
            @Valid @RequestBody TotpVerificationRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verificarTotp(request, extraerMetadata(httpRequest)));
    }

    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Renovar Access Token",
            description = "Rota un refresh token válido y emite un nuevo Access Token JWT junto con un nuevo refresh token"
    )
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, extraerMetadata(httpRequest)));
    }

    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cerrar sesión actual",
            description = "Revoca el refresh token enviado y/o invalida el Access Token Bearer mediante token_version"
    )
    public ResponseEntity<GenericResponse> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) @Valid RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        authService.logout(authorizationHeader, request, extraerMetadata(httpRequest));
        return ResponseEntity.ok(respuestaExitosa("Sesion cerrada exitosamente"));
    }

    @PostMapping(value = "/logout-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Cerrar todas las sesiones",
            description = "Revoca todos los refresh tokens activos del usuario autenticado e incrementa su token_version"
    )
    public ResponseEntity<GenericResponse> logoutAll(HttpServletRequest httpRequest) {
        authService.logoutAll(extraerMetadata(httpRequest));
        return ResponseEntity.ok(respuestaExitosa("Todas las sesiones fueron cerradas exitosamente"));
    }

    @GetMapping(value = "/auditoria", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Consultar auditoría de login (Solo ADMIN)",
            description = "Retorna el registro paginado de eventos de autenticación filtrado opcionalmente por correo"
    )
    public ResponseEntity<PageResponse<AuditoriaLoginResponse>> consultarAuditoria(
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(authService.consultarAuditoria(correo, pageable));
    }

    private GenericResponse respuestaExitosa(String mensaje) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(0);
        response.setMensaje(mensaje);
        return response;
    }

    private ClientMetadata extraerMetadata(HttpServletRequest request) {
        if (request == null) {
            return ClientMetadata.desconocido();
        }
        String ip = resolverIpOrigen(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return ClientMetadata.of(ip, userAgent);
    }

    private String resolverIpOrigen(HttpServletRequest request) {
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String primerIp = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            if (!primerIp.isBlank()) {
                return primerIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
