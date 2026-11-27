package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.RefreshToken;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.SesionActivaExistenteException;
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
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.CustomUserDetails;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.security.LoginRateLimiter;
import com.proyecto.servicios.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String MSG_CREDENCIALES_INVALIDAS = "Credenciales invalidas";
    private static final String MSG_CUENTA_BLOQUEADA = "La cuenta se encuentra bloqueada temporalmente. Intente mas tarde";
    private static final String MSG_USUARIO_INACTIVO = "El usuario se encuentra inactivo en el sistema";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CORREO_DESCONOCIDO = "desconocido";
    private static final String CLAIM_ROL = "rol";
    private static final String CLAIM_CLIENTE_ID = "clienteId";
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M1zP2xR8nM8sA7bK9u5m2vJ9u6G2";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAuditService loginAuditService;
    private final LoginRateLimiter loginRateLimiter;
    private final MfaService mfaService;
    private final MfaRecoveryService mfaRecoveryService;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.auth.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @Value("${app.auth.lockout-minutes:15}")
    private long lockoutMinutes = 15L;

    public AuthServiceImpl(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAuditService loginAuditService,
            LoginRateLimiter loginRateLimiter,
            MfaService mfaService,
            MfaRecoveryService mfaRecoveryService,
            EmailNotificationService emailNotificationService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAuditService = loginAuditService;
        this.loginRateLimiter = loginRateLimiter;
        this.mfaService = mfaService;
        this.mfaRecoveryService = mfaRecoveryService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class,
            SesionActivaExistenteException.class
    })
    public AuthResponse login(LoginRequest request) {
        return ejecutarLogin(request, ClientMetadata.desconocido());
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class,
            SesionActivaExistenteException.class
    })
    public AuthResponse login(LoginRequest request, ClientMetadata metadata) {
        return ejecutarLogin(request, metadata);
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class,
            SesionActivaExistenteException.class
    })
    public AuthResponse verificarTotp(TotpVerificationRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String challengeToken = request != null ? request.getChallengeToken() : null;
        String codigo = request != null ? request.getCodigo() : null;
        boolean forzarCierre = Boolean.TRUE.equals(request != null ? request.getForzarCierreSesionAnterior() : null);

        Usuario usuarioDesafio = mfaService.buscarUsuarioPorChallengeToken(challengeToken).orElse(null);
        boolean requirioEnrolamiento = usuarioDesafio != null && !usuarioDesafio.isTotpHabilitado();
        String correoRateLimit = usuarioDesafio != null ? usuarioDesafio.getCorreo() : null;
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), correoRateLimit);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (estaBloqueado(usuarioDesafio, now)) {
            loginAuditService.registrarEvento(
                    usuarioDesafio, usuarioDesafio.getCorreo(), LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }

        Usuario usuario;
        try {
            usuario = mfaService.validarDesafioYCodigo(challengeToken, codigo);
        } catch (BadCredentialsException ex) {
            if (usuarioDesafio == null) {
                usuarioDesafio = mfaService.buscarUsuarioPorChallengeToken(challengeToken).orElse(null);
            }
            if (usuarioDesafio != null) {
                int intentos = incrementarIntentosFallidos(usuarioDesafio);
                loginAuditService.registrarEvento(
                        usuarioDesafio, usuarioDesafio.getCorreo(), LoginAuditService.EVENTO_TOTP_INVALIDO, false, meta,
                        "Verificacion de desafio TOTP fallida");
                if (excedeLimiteIntentos(intentos)) {
                    throw bloquearUsuarioPorIntentos(
                            usuarioDesafio, usuarioDesafio.getCorreo(), meta, now,
                            "Cuenta bloqueada tras superar intentos fallidos de desafio TOTP");
                }
                usuarioRepository.save(usuarioDesafio);
            } else {
                loginAuditService.registrarEvento(
                        null, CORREO_DESCONOCIDO, LoginAuditService.EVENTO_TOTP_INVALIDO, false, meta,
                        "Verificacion de desafio TOTP fallida");
            }
            throw ex;
        }

        validarUsuarioYClienteActivos(usuario, usuario.getCorreo(), meta);
        validarOForzarSesionUnica(usuario, forzarCierre, meta);
        AuthResponse response = completarLoginExitoso(usuario, meta, now);
        if (requirioEnrolamiento && usuario.isTotpHabilitado()) {
            List<String> codigos = mfaRecoveryService.generarCodigosRecuperacion(usuario);
            response.setCodigosRecuperacion(codigos);
            if (emailNotificationService != null) {
                emailNotificationService.enviarCodigosRecuperacion2fa(
                        usuario.getCorreo(), obtenerNombreCompleto(usuario), codigos);
            }
        }
        return response;
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class,
            ReglaNegocioException.class
    })
    public void enviarOtpCorreo(EmailOtpSendRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String challengeToken = request != null ? request.getChallengeToken() : null;
        Usuario usuarioDesafio = mfaService.buscarUsuarioPorChallengeToken(challengeToken).orElse(null);
        String correoRateLimit = usuarioDesafio != null ? usuarioDesafio.getCorreo() : null;
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), correoRateLimit);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (estaBloqueado(usuarioDesafio, now)) {
            loginAuditService.registrarEvento(
                    usuarioDesafio, usuarioDesafio.getCorreo(), LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }
        if (usuarioDesafio != null) {
            validarUsuarioYClienteActivos(usuarioDesafio, usuarioDesafio.getCorreo(), meta);
        }

        MfaService.EmailOtpIssueResult issue = mfaService.generarYGuardarOtpCorreo(challengeToken);
        Usuario usuario = issue.usuario();
        if (emailNotificationService != null) {
            emailNotificationService.enviarCodigoOtpCorreo(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    issue.codigoOtp(),
                    issue.minutosVigencia(),
                    "Verificacion de acceso 2FA");
        }
        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_OTP_CORREO_ENVIADO, true, meta,
                "Codigo OTP enviado por correo electronico");
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class,
            ReglaNegocioException.class
    })
    public AuthResponse solicitarRecuperacionCorreo(EmailRecoveryStartRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String correo = normalizarCorreo(request != null ? request.getCorreo() : null);
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), correo);

        Usuario usuario = usuarioRepository.findWithClienteByCorreo(correo).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        if (estaBloqueado(usuario, now)) {
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }

        String rawPassword = request != null ? request.getPassword() : null;
        if (usuario == null) {
            if (rawPassword != null) {
                passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
            }
            throw registrarFalloPassword(null, correo, meta, now);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())) {
            throw registrarFalloPassword(usuario, correo, meta, now);
        }

        validarUsuarioYClienteActivos(usuario, correo, meta);
        MfaService.EmailRecoveryInitResult init = mfaService.iniciarDesafioRecuperacionCorreo(usuario, meta);
        if (emailNotificationService != null) {
            emailNotificationService.enviarCodigoOtpCorreo(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    init.codigoOtp(),
                    init.minutosVigencia(),
                    "Recuperacion de acceso 2FA");
        }

        loginAuditService.registrarEvento(
                usuario, correo, LoginAuditService.EVENTO_TOTP_RECUPERACION_CORREO_SOLICITADA, true, meta,
                "Codigo OTP de recuperacion 2FA enviado por correo");

        AuthResponse response = new AuthResponse();
        response.setCodigo(0);
        response.setMensaje("Codigo OTP de recuperacion enviado a tu correo electronico");
        response.setTipoToken(null);
        response.setCorreo(usuario.getCorreo());
        response.setRol(usuario.getRol());
        response.setClienteId(obtenerClienteId(usuario));
        response.setNombreCompleto(obtenerNombreCompleto(usuario));
        response.setRequiereTotp(true);
        response.setTotpConfiguracionRequerida(false);
        response.setChallengeToken(init.challengeToken());
        response.setOtpCorreoDisponible(true);
        response.setOtpCorreoEnviado(true);
        return response;
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class
    })
    public AuthResponse confirmarRecuperacionCorreo(EmailRecoveryConfirmRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String challengeToken = request != null ? request.getChallengeToken() : null;
        String codigo = request != null ? request.getCodigo() : null;

        Usuario usuarioDesafio = mfaService.buscarUsuarioPorChallengeToken(challengeToken).orElse(null);
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), usuarioDesafio != null ? usuarioDesafio.getCorreo() : null);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (estaBloqueado(usuarioDesafio, now)) {
            loginAuditService.registrarEvento(
                    usuarioDesafio, usuarioDesafio.getCorreo(), LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }

        Usuario usuario;
        try {
            usuario = mfaService.validarDesafioRecuperacionCorreo(challengeToken, codigo);
        } catch (BadCredentialsException ex) {
            if (usuarioDesafio == null) {
                usuarioDesafio = mfaService.buscarUsuarioPorChallengeToken(challengeToken).orElse(null);
            }
            if (usuarioDesafio != null) {
                int intentos = incrementarIntentosFallidos(usuarioDesafio);
                loginAuditService.registrarEvento(
                        usuarioDesafio, usuarioDesafio.getCorreo(), LoginAuditService.EVENTO_TOTP_RECUPERACION_FALLIDA, false, meta,
                        "Codigo OTP de recuperacion por correo invalido");
                if (excedeLimiteIntentos(intentos)) {
                    throw bloquearUsuarioPorIntentos(
                            usuarioDesafio, usuarioDesafio.getCorreo(), meta, now,
                            "Cuenta bloqueada tras superar intentos fallidos de recuperacion 2FA por correo");
                }
                usuarioRepository.save(usuarioDesafio);
            } else {
                loginAuditService.registrarEvento(
                        null, CORREO_DESCONOCIDO, LoginAuditService.EVENTO_TOTP_RECUPERACION_FALLIDA, false, meta,
                        "Codigo OTP de recuperacion por correo invalido");
            }
            throw ex;
        }

        validarUsuarioYClienteActivos(usuario, usuario.getCorreo(), meta);
        limpiarIntentosFallidos(usuario);
        usuario.setTotpHabilitado(false);
        usuario.setTotpSecretEnc(null);
        usuario.setTotpConfiguradoEn(null);
        usuario.setTotpUltimoPaso(null);
        usuario.incrementarTokenVersion();
        usuarioRepository.save(usuario);
        refreshTokenService.revocarTodasLasSesiones(usuario.getId());
        mfaRecoveryService.invalidarCodigos(usuario.getId());

        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_TOTP_RECUPERACION_EXITO, true, meta,
                "Recuperacion 2FA por correo aprobada; sesiones cerradas y reenrolamiento TOTP iniciado");
        if (emailNotificationService != null) {
            emailNotificationService.enviarAlertaSeguridad(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    "Recuperación de acceso 2FA por correo",
                    "Se restableció tu segundo factor mediante verificación por correo y se cerraron todas las sesiones activas.",
                    meta,
                    now);
        }
        return iniciarDesafioTotp(usuario, usuario.getCorreo(), meta);
    }

    @Override
    @Transactional(noRollbackFor = {
            BadCredentialsException.class,
            CuentaBloqueadaException.class,
            DisabledException.class
    })
    public AuthResponse recuperarAccesoTotp(TotpRecoveryRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String correo = normalizarCorreo(request != null ? request.getCorreo() : null);
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), correo);

        Usuario usuario = usuarioRepository.findWithClienteByCorreo(correo).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        if (estaBloqueado(usuario, now)) {
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }

        String rawPassword = request != null ? request.getPassword() : null;
        if (usuario == null) {
            if (rawPassword != null) {
                passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
            }
            throw registrarFalloPassword(null, correo, meta, now);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())) {
            throw registrarFalloPassword(usuario, correo, meta, now);
        }

        validarUsuarioYClienteActivos(usuario, correo, meta);

        String codigoRecuperacion = request.getCodigoRecuperacion();
        if (!mfaRecoveryService.consumirCodigoRecuperacion(usuario, codigoRecuperacion)) {
            int intentos = incrementarIntentosFallidos(usuario);
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_TOTP_RECUPERACION_FALLIDA, false, meta,
                    "Codigo de recuperacion 2FA invalido o ya utilizado");
            if (excedeLimiteIntentos(intentos)) {
                throw bloquearUsuarioPorIntentos(
                        usuario, correo, meta, now,
                        "Cuenta bloqueada tras superar intentos fallidos de recuperacion 2FA");
            }
            usuarioRepository.save(usuario);
            throw new BadCredentialsException("Codigo de recuperacion invalido o ya utilizado");
        }

        limpiarIntentosFallidos(usuario);
        usuario.setTotpHabilitado(false);
        usuario.setTotpSecretEnc(null);
        usuario.setTotpConfiguradoEn(null);
        usuario.setTotpUltimoPaso(null);
        usuario.incrementarTokenVersion();
        usuarioRepository.save(usuario);
        refreshTokenService.revocarTodasLasSesiones(usuario.getId());

        loginAuditService.registrarEvento(
                usuario, correo, LoginAuditService.EVENTO_TOTP_RECUPERACION_EXITO, true, meta,
                "Recuperacion 2FA aprobada; sesiones cerradas y reenrolamiento TOTP iniciado");
        if (emailNotificationService != null) {
            emailNotificationService.enviarAlertaSeguridad(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    "Recuperación de acceso 2FA con código de respaldo",
                    "Se utilizó un código de respaldo para restablecer tu 2FA y cerrar todas las sesiones activas.",
                    meta,
                    now);
        }
        return iniciarDesafioTotp(usuario, correo, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public SesionActivaResponse consultarSesionActiva() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InsufficientAuthenticationException("Se requiere autenticacion valida para consultar la sesion");
        }
        String correo = normalizarCorreo(auth.getName());
        Usuario usuario = usuarioRepository.findWithClienteByCorreo(correo)
                .orElseThrow(() -> new InsufficientAuthenticationException("Usuario autenticado no encontrado"));
        Optional<RefreshToken> sesionOpt = refreshTokenService.obtenerSesionActivaVigente(usuario.getId());
        if (sesionOpt.isEmpty()) {
            return SesionActivaResponse.builder()
                    .activa(false)
                    .correo(usuario.getCorreo())
                    .tokenVersion(usuario.getTokenVersion())
                    .build();
        }
        RefreshToken sesion = sesionOpt.get();
        return SesionActivaResponse.builder()
                .activa(true)
                .correo(usuario.getCorreo())
                .ipOrigen(sesion.getIpOrigen())
                .userAgent(sesion.getUserAgent())
                .fechaInicio(sesion.getFechaCreacion())
                .expiraEn(sesion.getExpiraEn())
                .tokenVersion(usuario.getTokenVersion())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public AuthResponse refresh(RefreshTokenRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), null);

        RefreshTokenService.RotationResult rotation = refreshTokenService.rotarRefreshToken(request.getRefreshToken(), meta);
        Usuario usuario = rotation.usuario();

        AuthResponse response = construirAuthResponseConTokens(usuario, rotation.nuevoRefreshToken());
        response.setMensaje("Token renovado exitosamente");
        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_REFRESH_EXITO, true, meta,
                "Refresh token rotado exitosamente");
        return response;
    }

    @Override
    @Transactional
    public void logout(String authorizationHeader, RefreshTokenRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        Optional<Usuario> usuarioDesdeRefresh = (request != null && tieneTexto(request.getRefreshToken()))
                ? refreshTokenService.revocarToken(request.getRefreshToken())
                : Optional.empty();

        Usuario usuarioJwt = invalidarJwtSiEsValido(authorizationHeader);
        Usuario usuario = usuarioJwt != null ? usuarioJwt : usuarioDesdeRefresh.orElse(null);
        String correo = usuario != null ? usuario.getCorreo() : CORREO_DESCONOCIDO;
        loginAuditService.registrarEvento(
                usuario, correo, LoginAuditService.EVENTO_LOGOUT, true, meta,
                "Cierre de sesion");
    }

    @Override
    @Transactional
    public void logoutAll(ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InsufficientAuthenticationException("Se requiere autenticacion valida para cerrar todas las sesiones");
        }

        String correo = normalizarCorreo(auth.getName());
        Usuario usuario = usuarioRepository.findWithClienteByCorreo(correo)
                .orElseThrow(() -> new InsufficientAuthenticationException("Usuario autenticado no encontrado"));

        refreshTokenService.revocarTodasLasSesiones(usuario.getId());
        usuario.incrementarTokenVersion();
        usuarioRepository.save(usuario);
        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_LOGOUT_ALL, true, meta,
                "Cierre global de todas las sesiones activas");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditoriaLoginResponse> consultarAuditoria(String correo, Pageable pageable) {
        return loginAuditService.consultarAuditoria(correo, pageable);
    }

    private AuthResponse ejecutarLogin(LoginRequest request, ClientMetadata metadata) {
        ClientMetadata meta = normalizarMetadata(metadata);
        String correo = normalizarCorreo(request != null ? request.getCorreo() : null);

        loginRateLimiter.verificarYRegistrarIntento(meta.ipOrigen(), correo);

        Usuario usuario = usuarioRepository.findWithClienteByCorreo(correo).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        if (estaBloqueado(usuario, now)) {
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta,
                    "Cuenta bloqueada temporalmente por intentos fallidos");
            throw new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
        }

        String rawPassword = request != null ? request.getPassword() : null;
        if (usuario == null) {
            if (rawPassword != null) {
                passwordEncoder.matches(rawPassword, DUMMY_BCRYPT_HASH);
            }
            throw registrarFalloPassword(null, correo, meta, now);
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())) {
            throw registrarFalloPassword(usuario, correo, meta, now);
        }

        validarUsuarioYClienteActivos(usuario, correo, meta);
        boolean forzarCierre = Boolean.TRUE.equals(request != null ? request.getForzarCierreSesionAnterior() : null);

        if (!usuario.isTotpObligatorio()) {
            validarOForzarSesionUnica(usuario, forzarCierre, meta);
            return completarLoginExitoso(usuario, meta, now);
        }

        String codigoTotp = request.getCodigoTotp();
        if (usuario.isTotpHabilitado() && tieneTexto(codigoTotp)) {
            return procesarLoginConTotpDirecto(usuario, codigoTotp.trim(), forzarCierre, correo, meta, now);
        }

        if (!forzarCierre) {
            verificarAusenciaDeSesionActiva(usuario, meta);
        }
        AuthResponse response = iniciarDesafioTotp(usuario, correo, meta);
        if ("EMAIL".equalsIgnoreCase(request.getCanalMfa())) {
            MfaService.EmailOtpIssueResult emailOtp = mfaService.generarYGuardarOtpCorreo(response.getChallengeToken());
            if (emailNotificationService != null) {
                emailNotificationService.enviarCodigoOtpCorreo(
                        usuario.getCorreo(),
                        obtenerNombreCompleto(usuario),
                        emailOtp.codigoOtp(),
                        emailOtp.minutosVigencia(),
                        "Inicio de sesion");
            }
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_OTP_CORREO_ENVIADO, true, meta,
                    "Codigo OTP enviado por correo electronico");
            response.setOtpCorreoEnviado(true);
            response.setMensaje("Codigo OTP enviado a tu correo electronico para verificar el acceso");
        }
        return response;
    }

    private boolean estaBloqueado(Usuario usuario, LocalDateTime now) {
        return usuario != null
                && usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(now);
    }

    private RuntimeException registrarFalloPassword(Usuario usuario, String correo, ClientMetadata meta, LocalDateTime now) {
        if (usuario == null) {
            loginAuditService.registrarEvento(
                    null, correo, LoginAuditService.EVENTO_PASSWORD_INVALIDA, false, meta,
                    "Intento de login para usuario inexistente");
            return new BadCredentialsException(MSG_CREDENCIALES_INVALIDAS);
        }

        int intentos = incrementarIntentosFallidos(usuario);
        if (excedeLimiteIntentos(intentos)) {
            return bloquearUsuarioPorIntentos(
                    usuario, correo, meta, now,
                    "Cuenta bloqueada tras superar intentos fallidos de contrasena");
        }

        usuarioRepository.save(usuario);
        loginAuditService.registrarEvento(
                usuario, correo, LoginAuditService.EVENTO_PASSWORD_INVALIDA, false, meta,
                "Contrasena incorrecta");
        return new BadCredentialsException(MSG_CREDENCIALES_INVALIDAS);
    }

    private AuthResponse procesarLoginConTotpDirecto(
            Usuario usuario,
            String codigoTotp,
            boolean forzarCierre,
            String correo,
            ClientMetadata meta,
            LocalDateTime now) {
        try {
            mfaService.validarCodigoDirectoEnLogin(usuario, codigoTotp);
        } catch (BadCredentialsException ex) {
            int intentos = incrementarIntentosFallidos(usuario);
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_TOTP_INVALIDO, false, meta,
                    "Codigo TOTP invalido en inicio de sesion");
            if (excedeLimiteIntentos(intentos)) {
                throw bloquearUsuarioPorIntentos(
                        usuario, correo, meta, now,
                        "Cuenta bloqueada tras superar intentos fallidos de codigo TOTP");
            }
            usuarioRepository.save(usuario);
            throw ex;
        }

        validarOForzarSesionUnica(usuario, forzarCierre, meta);
        return completarLoginExitoso(usuario, meta, now);
    }

    private void verificarAusenciaDeSesionActiva(Usuario usuario, ClientMetadata meta) {
        Optional<RefreshToken> sesionOpt = refreshTokenService.obtenerSesionActivaVigente(usuario.getId());
        if (sesionOpt.isPresent()) {
            RefreshToken sesion = sesionOpt.get();
            loginAuditService.registrarEvento(
                    usuario, usuario.getCorreo(), LoginAuditService.EVENTO_SESION_CONFLICTO, false, meta,
                    "Intento de inicio de sesion rechazado por sesion activa existente");
            throw new SesionActivaExistenteException(
                    "Ya existe una sesion activa en otro dispositivo. Debe cerrar la sesion anterior para continuar.",
                    sesion.getIpOrigen(),
                    sesion.getUserAgent(),
                    sesion.getFechaCreacion());
        }
    }

    private void validarOForzarSesionUnica(Usuario usuario, boolean forzarCierre, ClientMetadata meta) {
        Optional<RefreshToken> sesionOpt = refreshTokenService.obtenerSesionActivaVigente(usuario.getId());
        if (sesionOpt.isEmpty()) {
            return;
        }
        RefreshToken sesion = sesionOpt.get();
        if (!forzarCierre) {
            loginAuditService.registrarEvento(
                    usuario, usuario.getCorreo(), LoginAuditService.EVENTO_SESION_CONFLICTO, false, meta,
                    "Intento de inicio de sesion rechazado por sesion activa existente");
            throw new SesionActivaExistenteException(
                    "Ya existe una sesion activa en otro dispositivo. Debe cerrar la sesion anterior para continuar.",
                    sesion.getIpOrigen(),
                    sesion.getUserAgent(),
                    sesion.getFechaCreacion());
        }
        refreshTokenService.revocarTodasLasSesiones(usuario.getId());
        usuario.incrementarTokenVersion();
        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_SESION_FORZADA, true, meta,
                "Sesion anterior cerrada por confirmacion de nuevo inicio de sesion");
        if (emailNotificationService != null) {
            emailNotificationService.enviarAlertaSeguridad(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    "Cierre de sesión en dispositivo anterior",
                    "Se cerró tu sesión activa previa al autorizar un nuevo inicio de sesión.",
                    meta,
                    LocalDateTime.now(ZoneId.systemDefault()));
        }
    }

    private AuthResponse iniciarDesafioTotp(Usuario usuario, String correo, ClientMetadata meta) {
        MfaService.ChallengeInitResult challenge = mfaService.iniciarDesafio(usuario, meta);
        String evento = challenge.configuracionRequerida()
                ? LoginAuditService.EVENTO_TOTP_CONFIG_REQUERIDA
                : LoginAuditService.EVENTO_TOTP_REQUERIDO;
        String detalle = challenge.configuracionRequerida()
                ? "Configuracion obligatoria inicial de TOTP requerida"
                : "Verificacion TOTP requerida";
        loginAuditService.registrarEvento(usuario, correo, evento, true, meta, detalle);

        AuthResponse response = new AuthResponse();
        response.setCodigo(0);
        response.setMensaje(challenge.configuracionRequerida()
                ? "Configuracion de Google Authenticator requerida"
                : "Verificacion TOTP requerida");
        response.setTipoToken(null);
        response.setCorreo(usuario.getCorreo());
        response.setRol(usuario.getRol());
        response.setClienteId(obtenerClienteId(usuario));
        response.setNombreCompleto(obtenerNombreCompleto(usuario));
        response.setRequiereTotp(true);
        response.setTotpConfiguracionRequerida(challenge.configuracionRequerida());
        response.setChallengeToken(challenge.challengeToken());
        response.setOtpCorreoDisponible(true);
        if (challenge.configuracionRequerida()) {
            response.setTotpSecret(challenge.totpSecret());
            response.setOtpauthUri(challenge.otpauthUri());
        }
        return response;
    }

    private AuthResponse completarLoginExitoso(Usuario usuario, ClientMetadata meta, LocalDateTime now) {
        limpiarIntentosFallidos(usuario);
        usuario.setUltimoLogin(now);
        usuarioRepository.save(usuario);

        String refreshToken = refreshTokenService.emitirRefreshToken(usuario, meta);
        AuthResponse response = construirAuthResponseConTokens(usuario, refreshToken);
        loginAuditService.registrarEvento(
                usuario, usuario.getCorreo(), LoginAuditService.EVENTO_EXITO, true, meta,
                "Inicio de sesion exitoso");
        if (emailNotificationService != null) {
            emailNotificationService.enviarAlertaSeguridad(
                    usuario.getCorreo(),
                    obtenerNombreCompleto(usuario),
                    "Nuevo inicio de sesión exitoso",
                    "Se registró un inicio de sesión exitoso en tu cuenta.",
                    meta,
                    now);
        }
        return response;
    }

    private AuthResponse construirAuthResponseConTokens(Usuario usuario, String refreshToken) {
        CustomUserDetails userDetails = new CustomUserDetails(usuario);
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put(CLAIM_ROL, usuario.getRol());
        Long clienteId = obtenerClienteId(usuario);
        if (clienteId != null) {
            extraClaims.put(CLAIM_CLIENTE_ID, clienteId);
        }

        String accessToken = jwtService.generateToken(extraClaims, userDetails);
        String nombreCompleto = obtenerNombreCompleto(usuario);

        AuthResponse response = new AuthResponse(
                accessToken,
                usuario.getCorreo(),
                usuario.getRol(),
                clienteId,
                nombreCompleto);
        response.setRefreshToken(refreshToken);
        response.setExpiresInSegundos(jwtService.getJwtExpirationSeconds());
        response.setRequiereTotp(false);
        response.setTotpConfiguracionRequerida(false);
        return response;
    }

    private void validarUsuarioYClienteActivos(Usuario usuario, String correo, ClientMetadata meta) {
        if (!usuario.isActivo()) {
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_USUARIO_INACTIVO, false, meta,
                    "Intento de acceso con usuario inactivo");
            throw new DisabledException(MSG_USUARIO_INACTIVO);
        }
        if (usuario.getCliente() != null && !usuario.getCliente().isActivo()) {
            loginAuditService.registrarEvento(
                    usuario, correo, LoginAuditService.EVENTO_USUARIO_INACTIVO, false, meta,
                    "Intento de acceso con cliente asociado inactivo");
            throw new DisabledException("El cliente asociado se encuentra inactivo en el sistema");
        }
    }

    private int incrementarIntentosFallidos(Usuario usuario) {
        int actuales = usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos();
        int nuevos = actuales + 1;
        usuario.setIntentosFallidos(nuevos);
        return nuevos;
    }

    private boolean excedeLimiteIntentos(int intentos) {
        return intentos >= Math.max(1, maxFailedAttempts);
    }

    private CuentaBloqueadaException bloquearUsuarioPorIntentos(
            Usuario usuario,
            String correo,
            ClientMetadata meta,
            LocalDateTime now,
            String detalle) {
        usuario.setBloqueadoHasta(now.plusMinutes(Math.max(1L, lockoutMinutes)));
        usuarioRepository.save(usuario);
        loginAuditService.registrarEvento(
                usuario, correo, LoginAuditService.EVENTO_CUENTA_BLOQUEADA, false, meta, detalle);
        return new CuentaBloqueadaException(MSG_CUENTA_BLOQUEADA);
    }

    private void limpiarIntentosFallidos(Usuario usuario) {
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
    }

    private Usuario invalidarJwtSiEsValido(String authorizationHeader) {
        if (!tieneTexto(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String jwt = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (jwt.isEmpty()) {
            return null;
        }
        try {
            String extractedUsername = jwtService.extractUsername(jwt);
            if (!tieneTexto(extractedUsername)) {
                return null;
            }
            String correoNorm = normalizarCorreo(extractedUsername);
            Usuario usuario = usuarioRepository.findWithClienteByCorreo(correoNorm).orElse(null);
            if (usuario != null && jwtService.isTokenValid(jwt, new CustomUserDetails(usuario))) {
                usuario.incrementarTokenVersion();
                return usuarioRepository.save(usuario);
            }
        } catch (RuntimeException ex) {
            log.debug("Token JWT no valido al procesar logout ({})", ex.getClass().getSimpleName());
        }
        return null;
    }

    private ClientMetadata normalizarMetadata(ClientMetadata metadata) {
        return metadata != null ? metadata : ClientMetadata.desconocido();
    }

    private String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private Long obtenerClienteId(Usuario usuario) {
        return usuario.getCliente() != null ? usuario.getCliente().getId() : null;
    }

    private String obtenerNombreCompleto(Usuario usuario) {
        if (usuario.getCliente() == null) {
            return usuario.getCorreo();
        }
        String nombre = usuario.getCliente().getNombre();
        String apellido = usuario.getCliente().getApellidoPaterno();
        if (nombre == null && apellido == null) {
            return usuario.getCorreo();
        }
        return ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
    }
}
