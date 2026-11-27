package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.model.seguridad.EmailOtpSendRequest;
import com.proyecto.servicios.model.seguridad.EmailRecoveryConfirmRequest;
import com.proyecto.servicios.model.seguridad.EmailRecoveryStartRequest;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.model.seguridad.RefreshTokenRequest;
import com.proyecto.servicios.model.seguridad.SesionActivaResponse;
import com.proyecto.servicios.model.seguridad.TotpRecoveryRequest;
import com.proyecto.servicios.model.seguridad.TotpVerificationRequest;
import com.proyecto.servicios.security.ClientIpResolver;
import com.proyecto.servicios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final AuthService authService;

    @Value("${app.auth.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders = false;

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

    @PostMapping(value = "/totp/recuperar", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Recuperar acceso 2FA con código de respaldo",
            description = "Valida correo, contraseña y un código de recuperación de un solo uso para cerrar sesiones previas e iniciar un nuevo enrolamiento TOTP"
    )
    public ResponseEntity<AuthResponse> recuperarAccesoTotp(
            @Valid @RequestBody TotpRecoveryRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.recuperarAccesoTotp(request, extraerMetadata(httpRequest)));
    }

    @PostMapping(value = "/otp/correo/enviar", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Enviar código OTP temporal por correo electrónico",
            description = "Genera y envía al correo del usuario un código temporal de 6 dígitos asociado al challengeToken activo"
    )
    public ResponseEntity<GenericResponse> enviarOtpCorreo(
            @Valid @RequestBody EmailOtpSendRequest request,
            HttpServletRequest httpRequest) {
        authService.enviarOtpCorreo(request, extraerMetadata(httpRequest));
        return ResponseEntity.ok(respuestaExitosa("Codigo OTP enviado al correo electronico registrado"));
    }

    @PostMapping(value = "/totp/recuperar/solicitar-correo", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Solicitar código por correo para recuperar 2FA",
            description = "Valida correo y contraseña y envía un OTP temporal por correo para restablecer el 2FA"
    )
    public ResponseEntity<AuthResponse> solicitarRecuperacionCorreo(
            @Valid @RequestBody EmailRecoveryStartRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.solicitarRecuperacionCorreo(request, extraerMetadata(httpRequest)));
    }

    @PostMapping(value = "/totp/recuperar/confirmar-correo", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Confirmar recuperación 2FA mediante OTP por correo",
            description = "Valida el OTP recibido por correo, cierra todas las sesiones previas e inicia un nuevo enrolamiento TOTP"
    )
    public ResponseEntity<AuthResponse> confirmarRecuperacionCorreo(
            @Valid @RequestBody EmailRecoveryConfirmRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.confirmarRecuperacionCorreo(request, extraerMetadata(httpRequest)));
    }

    @GetMapping(value = "/sesion-activa", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Consultar sesión activa vigente",
            description = "Retorna los metadatos de la sesión activa única registrada en servidor para el usuario autenticado"
    )
    public ResponseEntity<SesionActivaResponse> consultarSesionActiva() {
        return ResponseEntity.ok(authService.consultarSesionActiva());
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
        if (page < 0) {
            throw new ReglaNegocioException("El numero de pagina no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReglaNegocioException("El tamano de pagina debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
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
        String ip = ClientIpResolver.resolverIpOrigen(request, trustForwardedHeaders);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return ClientMetadata.of(ip, userAgent);
    }
}
