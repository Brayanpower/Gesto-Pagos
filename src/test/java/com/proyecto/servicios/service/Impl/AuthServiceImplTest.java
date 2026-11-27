package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.seguridad.RefreshToken;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de AuthServiceImpl")
class AuthServiceImplTest {

    private static final String CORREO = "juan.garcia@banco.com";
    private static final String PASSWORD = "Banco123@";
    private static final String HASH = "$2a$10$hash";
    private static final ClientMetadata METADATA = ClientMetadata.of("203.0.113.10", "JUnit-Client");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAuditService loginAuditService;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private MfaService mfaService;
    @Mock private MfaRecoveryService mfaRecoveryService;
    @Mock private EmailNotificationService emailNotificationService;

    @InjectMocks
    private AuthServiceImpl servicio;

    @AfterEach
    void limpiarSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Login con totpObligatorio=false (desactivado en BD) emite Access Token y Refresh Token directamente")
    void login_totpObligatorioFalse_devuelveTokens() {
        Usuario usuario = usuarioActivo(false, false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-token-value-12345");

        AuthResponse response = servicio.login(new LoginRequest("Juan.Garcia@Banco.com", PASSWORD), METADATA);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("refresh-token-value-12345", response.getRefreshToken());
        assertEquals(900L, response.getExpiresInSegundos());
        assertFalse(response.getRequiereTotp());
        assertFalse(response.getTotpConfiguracionRequerida());
        assertEquals(CORREO, response.getCorreo());
        assertEquals(1L, response.getClienteId());
        assertNotNull(usuario.getUltimoLogin());

        verify(loginRateLimiter).verificarYRegistrarIntento("203.0.113.10", CORREO);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_EXITO), eq(true), eq(METADATA), anyString());
        verify(mfaService, never()).iniciarDesafio(any(), any());
    }

    @Test
    @DisplayName("Sobrecarga compatible login(LoginRequest) opera con ClientMetadata desconocido")
    void login_sobrecargaSinMetadata_funciona() {
        Usuario usuario = usuarioActivo(false, false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(eq(usuario), any(ClientMetadata.class)))
                .thenReturn("refresh-token-value-12345");

        AuthResponse response = servicio.login(new LoginRequest(CORREO, PASSWORD));

        assertEquals("jwt-token", response.getToken());
        verify(loginRateLimiter).verificarYRegistrarIntento(null, CORREO);
    }

    @Test
    @DisplayName("Primer login con TOTP obligatorio por defecto retorna challenge de configuracion SIN JWT ni refresh")
    void login_primeraVezTotpObligatorio_devuelveChallengeSinJwt() {
        Usuario usuario = usuarioActivo(true, false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaService.iniciarDesafio(usuario, METADATA)).thenReturn(
                new MfaService.ChallengeInitResult(
                        "challenge-enroll-123",
                        true,
                        "BASE32SECRET",
                        "otpauth://totp/SistemaBancario:juan.garcia@banco.com?secret=BASE32SECRET"));

        AuthResponse response = servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA);

        assertNull(response.getToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getTipoToken());
        assertNull(response.getExpiresInSegundos());
        assertTrue(response.getRequiereTotp());
        assertTrue(response.getTotpConfiguracionRequerida());
        assertEquals("challenge-enroll-123", response.getChallengeToken());
        assertEquals("BASE32SECRET", response.getTotpSecret());
        assertNotNull(response.getOtpauthUri());

        verify(jwtService, never()).generateToken(anyMap(), any());
        verify(refreshTokenService, never()).emitirRefreshToken(any(), any());
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_CONFIG_REQUERIDA),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Segundo login con TOTP habilitado en 2 pasos (sin codigoTotp) retorna challenge sin exponer secreto")
    void login_segundoIngresoDosPasos_devuelveChallengeSinSecretoNiJwt() {
        Usuario usuario = usuarioActivo(true, true);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaService.iniciarDesafio(usuario, METADATA))
                .thenReturn(new MfaService.ChallengeInitResult("challenge-verify-456", false, null, null));

        AuthResponse response = servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA);

        assertNull(response.getToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getTotpSecret());
        assertNull(response.getOtpauthUri());
        assertTrue(response.getRequiereTotp());
        assertFalse(response.getTotpConfiguracionRequerida());
        assertEquals("challenge-verify-456", response.getChallengeToken());

        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_REQUERIDO),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Segundo login con TOTP habilitado en 1 paso (con codigoTotp valido) emite Access y Refresh Token")
    void login_segundoIngresoUnPasoConCodigoValido_emiteTokens() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setIntentosFallidos(2);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-1step");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-1step");

        AuthResponse response = servicio.login(new LoginRequest(CORREO, PASSWORD, "123456"), METADATA);

        assertEquals("jwt-1step", response.getToken());
        assertEquals("refresh-1step", response.getRefreshToken());
        assertFalse(response.getRequiereTotp());
        assertEquals(0, usuario.getIntentosFallidos());
        verify(mfaService).validarCodigoDirectoEnLogin(usuario, "123456");
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_EXITO), eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Segundo login en 1 paso con codigoTotp invalido incrementa intentos, audita TOTP_INVALIDO y bloquea al exceder")
    void login_segundoIngresoTotpInvalido_incrementaYBloqueaAlExceder() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setIntentosFallidos(3);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        doThrow(new BadCredentialsException("Codigo TOTP invalido"))
                .when(mfaService).validarCodigoDirectoEnLogin(usuario, "999999");

        LoginRequest req = new LoginRequest(CORREO, PASSWORD, "999999");
        assertThrows(BadCredentialsException.class, () -> servicio.login(req, METADATA));
        assertEquals(4, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());

        assertThrows(CuentaBloqueadaException.class, () -> servicio.login(req, METADATA));
        assertEquals(5, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());

        verify(loginAuditService, times(2)).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_INVALIDO),
                eq(false), eq(METADATA), anyString());
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_CUENTA_BLOQUEADA),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("verificarTotp valido valida activos, actualiza ultimoLogin, emite tokens y audita EXITO")
    void verificarTotp_valido_emiteTokensYAudita() {
        Usuario usuario = usuarioActivo(true, true);
        TotpVerificationRequest request = new TotpVerificationRequest("chal-1", "123456");

        when(mfaService.validarDesafioYCodigo("chal-1", "123456")).thenReturn(usuario);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-verified");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-verified");

        AuthResponse response = servicio.verificarTotp(request, METADATA);

        assertEquals("jwt-verified", response.getToken());
        assertEquals("refresh-verified", response.getRefreshToken());
        assertFalse(response.getRequiereTotp());
        assertNotNull(usuario.getUltimoLogin());
        verify(loginRateLimiter).verificarYRegistrarIntento("203.0.113.10", null);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_EXITO), eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("verificarTotp con usuario o cliente inactivo lanza DisabledException y con codigo invalido audita TOTP_INVALIDO")
    void verificarTotp_inactivoYCodigoInvalido() {
        Usuario usuarioInactivo = usuarioActivo(true, true);
        usuarioInactivo.setActivo(false);
        when(mfaService.validarDesafioYCodigo("chal-inactivo", "123456")).thenReturn(usuarioInactivo);

        assertThrows(DisabledException.class,
                () -> servicio.verificarTotp(new TotpVerificationRequest("chal-inactivo", "123456"), METADATA));
        verify(loginAuditService).registrarEvento(
                eq(usuarioInactivo), eq(CORREO), eq(LoginAuditService.EVENTO_USUARIO_INACTIVO),
                eq(false), eq(METADATA), anyString());

        when(mfaService.validarDesafioYCodigo("chal-malo", "000000"))
                .thenThrow(new BadCredentialsException("Codigo invalido"));
        assertThrows(BadCredentialsException.class,
                () -> servicio.verificarTotp(new TotpVerificationRequest("chal-malo", "000000"), METADATA));
        verify(loginAuditService).registrarEvento(
                eq(null), eq("desconocido"), eq(LoginAuditService.EVENTO_TOTP_INVALIDO),
                eq(false), eq(METADATA), anyString());

        Usuario usuarioConDesafio = usuarioActivo(true, true);
        when(mfaService.validarDesafioYCodigo("chal-user", "999999"))
                .thenThrow(new BadCredentialsException("Codigo TOTP invalido"));
        when(mfaService.buscarUsuarioPorChallengeToken("chal-user")).thenReturn(Optional.of(usuarioConDesafio));
        assertThrows(BadCredentialsException.class,
                () -> servicio.verificarTotp(new TotpVerificationRequest("chal-user", "999999"), METADATA));
        verify(loginAuditService).registrarEvento(
                eq(usuarioConDesafio), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_INVALIDO),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("verificarTotp acumula intentosFallidos, bloquea al alcanzar el maximo y rechaza si ya esta bloqueado")
    void verificarTotp_fallosSucesivos_bloqueanCuentaYRechazanPosterior() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setIntentosFallidos(3);
        when(mfaService.buscarUsuarioPorChallengeToken("chal-lock")).thenReturn(Optional.of(usuario));
        when(mfaService.validarDesafioYCodigo("chal-lock", "000000"))
                .thenThrow(new BadCredentialsException("Codigo TOTP invalido"));

        TotpVerificationRequest req = new TotpVerificationRequest("chal-lock", "000000");
        assertThrows(BadCredentialsException.class, () -> servicio.verificarTotp(req, METADATA));
        assertEquals(4, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());

        assertThrows(CuentaBloqueadaException.class, () -> servicio.verificarTotp(req, METADATA));
        assertEquals(5, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());

        assertThrows(CuentaBloqueadaException.class, () -> servicio.verificarTotp(req, METADATA));
        verify(loginRateLimiter, times(3)).verificarYRegistrarIntento("203.0.113.10", CORREO);
        verify(mfaService, times(2)).validarDesafioYCodigo("chal-lock", "000000");
        verify(loginAuditService, times(2)).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_CUENTA_BLOQUEADA),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Iniciar desafio TOTP no borra intentos fallidos previos antes de validar el segundo factor")
    void login_iniciarDesafioTotp_conservaIntentosFallidosHastaVerificarTotp() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setIntentosFallidos(2);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaService.iniciarDesafio(usuario, METADATA))
                .thenReturn(new MfaService.ChallengeInitResult("chal-keep", false, null, null));

        AuthResponse response = servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA);

        assertTrue(response.getRequiereTotp());
        assertEquals(2, usuario.getIntentosFallidos());
    }

    @Test
    @DisplayName("Bloqueo automatico tras 5 intentos fallidos de contrasena y rechazo de intentos posteriores")
    void login_cincoIntentosFallidos_bloqueaCuenta() {
        Usuario usuario = usuarioActivo(true, false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Mala123@", HASH)).thenReturn(false);

        LoginRequest req = new LoginRequest(CORREO, "Mala123@");
        for (int i = 1; i <= 4; i++) {
            assertThrows(BadCredentialsException.class, () -> servicio.login(req, METADATA));
            assertEquals(i, usuario.getIntentosFallidos());
            assertNull(usuario.getBloqueadoHasta());
        }

        assertThrows(CuentaBloqueadaException.class, () -> servicio.login(req, METADATA));
        assertEquals(5, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());

        assertThrows(CuentaBloqueadaException.class, () -> servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA));
        verify(passwordEncoder, never()).matches(PASSWORD, HASH);
    }

    @Test
    @DisplayName("Anti-enumeracion: usuario inactivo con contrasena incorrecta recibe BadCredentialsException y NO DisabledException")
    void login_antiEnumeracion_usuarioInactivoPasswordIncorrecta_lanzaBadCredentials() {
        Usuario usuario = usuarioActivo(true, false);
        usuario.setActivo(false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Incorrecta1@", HASH)).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> servicio.login(new LoginRequest(CORREO, "Incorrecta1@"), METADATA));
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_PASSWORD_INVALIDA),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Usuario inactivo o cliente inactivo con contrasena correcta lanza DisabledException")
    void login_usuarioOClienteInactivoConPasswordCorrecta_lanzaDisabled() {
        Usuario usuario = usuarioActivo(true, false);
        usuario.setActivo(false);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThrows(DisabledException.class, () -> servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA));

        usuario.setActivo(true);
        usuario.getCliente().setActivo(false);
        assertThrows(DisabledException.class, () -> servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA));
        verify(loginAuditService, times(2)).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_USUARIO_INACTIVO),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Usuario inexistente ejecuta BCrypt dummy anti-timing, audita PASSWORD_INVALIDA y lanza BadCredentialsException")
    void login_usuarioInexistente_auditaYLanzaBadCredentials() {
        when(usuarioRepository.findWithClienteByCorreo("nadie@banco.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> servicio.login(new LoginRequest("nadie@banco.com", PASSWORD), METADATA));
        verify(passwordEncoder).matches(eq(PASSWORD), anyString());
        verify(loginAuditService).registrarEvento(
                eq(null), eq("nadie@banco.com"), eq(LoginAuditService.EVENTO_PASSWORD_INVALIDA),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Refresh token rota token, emite nuevo Access Token y audita REFRESH_EXITO")
    void refresh_valido_emiteNuevoAccessYRefreshToken() {
        Usuario usuario = usuarioActivo(true, true);
        RefreshTokenRequest req = new RefreshTokenRequest("refresh-raw-token-1234567890");
        when(refreshTokenService.rotarRefreshToken("refresh-raw-token-1234567890", METADATA))
                .thenReturn(new RefreshTokenService.RotationResult(usuario, "refresh-rotated-1234567890"));
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-refreshed");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);

        AuthResponse response = servicio.refresh(req, METADATA);

        assertEquals("jwt-refreshed", response.getToken());
        assertEquals("refresh-rotated-1234567890", response.getRefreshToken());
        assertEquals(900L, response.getExpiresInSegundos());
        verify(loginRateLimiter).verificarYRegistrarIntento("203.0.113.10", null);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_REFRESH_EXITO),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Logout revoca refresh token, incrementa tokenVersion si el Bearer JWT es valido y audita LOGOUT")
    void logout_conRefreshYBearerValido_revocaEIncrementaTokenVersion() {
        Usuario usuario = usuarioActivo(true, true);
        RefreshTokenRequest req = new RefreshTokenRequest("refresh-raw-token-1234567890");

        when(jwtService.extractUsername("valid-jwt")).thenReturn(CORREO);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(jwtService.isTokenValid(eq("valid-jwt"), any(CustomUserDetails.class))).thenReturn(true);
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        servicio.logout("Bearer valid-jwt", req, METADATA);

        assertEquals(1L, usuario.getTokenVersion());
        verify(refreshTokenService).revocarToken("refresh-raw-token-1234567890");
        verify(usuarioRepository).save(usuario);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_LOGOUT), eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Logout solo con refresh token y sin Bearer JWT no falla y audita usuario recuperado del refresh token")
    void logout_sinBearer_revocaRefreshYAudita() {
        Usuario usuario = usuarioActivo(true, true);
        RefreshTokenRequest req = new RefreshTokenRequest("refresh-raw-token-1234567890");
        when(refreshTokenService.revocarToken("refresh-raw-token-1234567890")).thenReturn(Optional.of(usuario));

        servicio.logout(null, req, METADATA);

        verify(refreshTokenService).revocarToken("refresh-raw-token-1234567890");
        verify(usuarioRepository, never()).save(any());
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_LOGOUT), eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("LogoutAll revoca todas las sesiones del usuario autenticado, incrementa tokenVersion y audita LOGOUT_ALL")
    void logoutAll_usuarioAutenticado_revocaTodasLasSesiones() {
        Usuario usuario = usuarioActivo(true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CORREO, "n/a", List.of()));
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        servicio.logoutAll(METADATA);

        assertEquals(1L, usuario.getTokenVersion());
        verify(refreshTokenService).revocarTodasLasSesiones(10L);
        verify(usuarioRepository).save(usuario);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_LOGOUT_ALL),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("consultarAuditoria delega correctamente a LoginAuditService")
    void consultarAuditoria_delegaALoginAuditService() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<AuditoriaLoginResponse> expected = PageResponse.<AuditoriaLoginResponse>builder()
                .contenido(List.of())
                .paginaActual(0)
                .tamanoPagina(20)
                .totalElementos(0L)
                .totalPaginas(0)
                .primera(true)
                .ultima(true)
                .build();
        when(loginAuditService.consultarAuditoria(CORREO, pageable)).thenReturn(expected);

        PageResponse<AuditoriaLoginResponse> result = servicio.consultarAuditoria(CORREO, pageable);

        assertEquals(expected, result);
        verify(loginAuditService).consultarAuditoria(CORREO, pageable);
    }

    @Test
    @DisplayName("Sesion unica: login con sesion activa y sin forzar lanza SesionActivaExistenteException sin emitir JWT")
    void login_conSesionActivaSinForzar_lanzaSesionActivaExistente() {
        Usuario usuario = usuarioActivo(true, true);
        RefreshToken sesionPrevia = new RefreshToken();
        sesionPrevia.setIpOrigen("10.0.0.5");
        sesionPrevia.setUserAgent("FlutterApp/1.0");
        sesionPrevia.setFechaCreacion(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(10));

        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(refreshTokenService.obtenerSesionActivaVigente(10L)).thenReturn(Optional.of(sesionPrevia));

        SesionActivaExistenteException ex = assertThrows(
                SesionActivaExistenteException.class,
                () -> servicio.login(new LoginRequest(CORREO, PASSWORD), METADATA));

        assertEquals("10.0.0.5", ex.getIpOrigenActiva());
        assertEquals("FlutterApp/1.0", ex.getUserAgentActivo());
        verify(jwtService, never()).generateToken(anyMap(), any());
        verify(refreshTokenService, never()).revocarTodasLasSesiones(any());
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_SESION_CONFLICTO),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("Sesion unica: login 1 paso con forzarCierreSesionAnterior=true revoca sesion previa, incrementa tokenVersion y emite JWT")
    void login_conSesionActivaYForzarCierre_revocaAnteriorYEmiteTokens() {
        Usuario usuario = usuarioActivo(true, true);
        RefreshToken sesionPrevia = new RefreshToken();
        sesionPrevia.setIpOrigen("10.0.0.5");
        sesionPrevia.setUserAgent("FlutterApp/1.0");

        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(refreshTokenService.obtenerSesionActivaVigente(10L)).thenReturn(Optional.of(sesionPrevia));
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-web-nuevo");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-web-nuevo");

        LoginRequest req = new LoginRequest(CORREO, PASSWORD, "123456");
        req.setForzarCierreSesionAnterior(true);

        AuthResponse response = servicio.login(req, METADATA);

        assertEquals("jwt-web-nuevo", response.getToken());
        assertEquals(1L, usuario.getTokenVersion());
        verify(mfaService).validarCodigoDirectoEnLogin(usuario, "123456");
        verify(refreshTokenService).revocarTodasLasSesiones(10L);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_SESION_FORZADA),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("verificarTotp tras enrolamiento inicial devuelve codigos de recuperacion y aplica control de sesion unica")
    void verificarTotp_enrolamiento_devuelveCodigosRecuperacion() {
        Usuario usuario = usuarioActivo(true, false);
        when(mfaService.buscarUsuarioPorChallengeToken("chal-enroll")).thenReturn(Optional.of(usuario));
        when(mfaService.validarDesafioYCodigo("chal-enroll", "123456")).thenAnswer(inv -> {
            usuario.setTotpHabilitado(true);
            return usuario;
        });
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-enrolled");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-enrolled");
        when(mfaRecoveryService.generarCodigosRecuperacion(usuario)).thenReturn(List.of("ABCD-2345", "WXYZ-6789"));

        AuthResponse response = servicio.verificarTotp(new TotpVerificationRequest("chal-enroll", "123456"), METADATA);

        assertEquals("jwt-enrolled", response.getToken());
        assertEquals(List.of("ABCD-2345", "WXYZ-6789"), response.getCodigosRecuperacion());
        verify(mfaRecoveryService).generarCodigosRecuperacion(usuario);
    }

    @Test
    @DisplayName("recuperarAccesoTotp valido resetea TOTP, revoca sesiones, incrementa tokenVersion e inicia nuevo enrollment")
    void recuperarAccesoTotp_valido_reseteaTotpEIniciaEnrollment() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setTotpSecretEnc("ENC:viejo");
        usuario.setTotpUltimoPaso(55L);

        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaRecoveryService.consumirCodigoRecuperacion(usuario, "ABCD-2345")).thenReturn(true);
        when(mfaService.iniciarDesafio(usuario, METADATA)).thenReturn(
                new MfaService.ChallengeInitResult("chal-recovery", true, "NEWSECRET", "otpauth://totp/nuevo"));

        AuthResponse response = servicio.recuperarAccesoTotp(
                new TotpRecoveryRequest(CORREO, PASSWORD, "ABCD-2345"), METADATA);

        assertTrue(response.getRequiereTotp());
        assertTrue(response.getTotpConfiguracionRequerida());
        assertEquals("chal-recovery", response.getChallengeToken());
        assertEquals("NEWSECRET", response.getTotpSecret());
        assertFalse(usuario.isTotpHabilitado());
        assertNull(usuario.getTotpSecretEnc());
        assertNull(usuario.getTotpUltimoPaso());
        assertEquals(1L, usuario.getTokenVersion());
        verify(refreshTokenService).revocarTodasLasSesiones(10L);
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_RECUPERACION_EXITO),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("recuperarAccesoTotp con codigo invalido incrementa intentos fallidos, audita y bloquea al exceder")
    void recuperarAccesoTotp_codigoInvalido_bloqueaAlExceder() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setIntentosFallidos(4);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaRecoveryService.consumirCodigoRecuperacion(usuario, "ZZZZ-9999")).thenReturn(false);

        assertThrows(CuentaBloqueadaException.class, () -> servicio.recuperarAccesoTotp(
                new TotpRecoveryRequest(CORREO, PASSWORD, "ZZZZ-9999"), METADATA));

        assertEquals(5, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_TOTP_RECUPERACION_FALLIDA),
                eq(false), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("consultarSesionActiva retorna metadatos cuando existe sesion vigente y activa=false si no")
    void consultarSesionActiva_retornaEstado() {
        Usuario usuario = usuarioActivo(true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CORREO, "n/a", List.of()));
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        RefreshToken sesion = new RefreshToken();
        sesion.setIpOrigen("198.51.100.40");
        sesion.setUserAgent("WebBrowser/1.0");
        sesion.setFechaCreacion(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(5));
        sesion.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusDays(7));
        when(refreshTokenService.obtenerSesionActivaVigente(10L))
                .thenReturn(Optional.of(sesion))
                .thenReturn(Optional.empty());

        SesionActivaResponse activa = servicio.consultarSesionActiva();
        assertTrue(activa.isActiva());
        assertEquals("198.51.100.40", activa.getIpOrigen());
        assertEquals("WebBrowser/1.0", activa.getUserAgent());

        SesionActivaResponse inactiva = servicio.consultarSesionActiva();
        assertFalse(inactiva.isActiva());
    }

    @Test
    @DisplayName("Login con canalMfa=EMAIL envia codigo OTP al correo y marca otpCorreoEnviado=true")
    void login_canalMfaEmail_enviaOtpPorCorreo() {
        Usuario usuario = usuarioActivo(true, true);
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaService.iniciarDesafio(usuario, METADATA))
                .thenReturn(new MfaService.ChallengeInitResult("chal-email-1", false, null, null));
        when(mfaService.generarYGuardarOtpCorreo("chal-email-1"))
                .thenReturn(new MfaService.EmailOtpIssueResult(usuario, "654321", 5L));

        LoginRequest req = new LoginRequest(CORREO, PASSWORD);
        req.setCanalMfa("EMAIL");

        AuthResponse response = servicio.login(req, METADATA);

        assertTrue(response.getRequiereTotp());
        assertTrue(response.getOtpCorreoDisponible());
        assertTrue(response.getOtpCorreoEnviado());
        verify(emailNotificationService).enviarCodigoOtpCorreo(
                CORREO, "Juan Garcia", "654321", 5L, "Inicio de sesion");
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_OTP_CORREO_ENVIADO),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("enviarOtpCorreo genera y envia codigo OTP por correo y audita evento")
    void enviarOtpCorreo_exito_enviaCorreoYAudita() {
        Usuario usuario = usuarioActivo(true, true);
        when(mfaService.buscarUsuarioPorChallengeToken("chal-99")).thenReturn(Optional.of(usuario));
        when(mfaService.generarYGuardarOtpCorreo("chal-99"))
                .thenReturn(new MfaService.EmailOtpIssueResult(usuario, "112233", 5L));

        servicio.enviarOtpCorreo(new EmailOtpSendRequest("chal-99"), METADATA);

        verify(emailNotificationService).enviarCodigoOtpCorreo(
                CORREO, "Juan Garcia", "112233", 5L, "Verificacion de acceso 2FA");
        verify(loginAuditService).registrarEvento(
                eq(usuario), eq(CORREO), eq(LoginAuditService.EVENTO_OTP_CORREO_ENVIADO),
                eq(true), eq(METADATA), anyString());
    }

    @Test
    @DisplayName("solicitarRecuperacionCorreo y confirmarRecuperacionCorreo completan recuperacion 2FA por email")
    void recuperacion2faPorCorreo_flujoCompleto() {
        Usuario usuario = usuarioActivo(true, true);
        usuario.setTotpSecretEnc("ENC:viejo");
        when(usuarioRepository.findWithClienteByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(mfaService.iniciarDesafioRecuperacionCorreo(usuario, METADATA))
                .thenReturn(new MfaService.EmailRecoveryInitResult("chal-rec-mail", usuario, "778899", 5L));

        AuthResponse inicio = servicio.solicitarRecuperacionCorreo(
                new EmailRecoveryStartRequest(CORREO, PASSWORD), METADATA);

        assertTrue(inicio.getRequiereTotp());
        assertTrue(inicio.getOtpCorreoEnviado());
        assertEquals("chal-rec-mail", inicio.getChallengeToken());
        verify(emailNotificationService).enviarCodigoOtpCorreo(
                CORREO, "Juan Garcia", "778899", 5L, "Recuperacion de acceso 2FA");

        when(mfaService.buscarUsuarioPorChallengeToken("chal-rec-mail")).thenReturn(Optional.of(usuario));
        when(mfaService.validarDesafioRecuperacionCorreo("chal-rec-mail", "778899")).thenReturn(usuario);
        when(mfaService.iniciarDesafio(usuario, METADATA))
                .thenReturn(new MfaService.ChallengeInitResult("chal-new-enroll", true, "NEWSECRET", "otpauth://totp/nuevo"));

        AuthResponse confirmado = servicio.confirmarRecuperacionCorreo(
                new EmailRecoveryConfirmRequest("chal-rec-mail", "778899"), METADATA);

        assertTrue(confirmado.getTotpConfiguracionRequerida());
        assertEquals("chal-new-enroll", confirmado.getChallengeToken());
        assertFalse(usuario.isTotpHabilitado());
        assertNull(usuario.getTotpSecretEnc());
        assertEquals(1L, usuario.getTokenVersion());
        verify(refreshTokenService).revocarTodasLasSesiones(10L);
        verify(mfaRecoveryService).invalidarCodigos(10L);
        verify(emailNotificationService).enviarAlertaSeguridad(
                eq(CORREO), eq("Juan Garcia"), anyString(), anyString(), eq(METADATA), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("verificarTotp en primer enrolamiento envia codigos de respaldo y alerta de seguridad por correo")
    void verificarTotp_enrolamiento_enviaCorreosDeRespaldoYAlerta() {
        Usuario usuario = usuarioActivo(true, false);
        when(mfaService.buscarUsuarioPorChallengeToken("chal-mail-enroll")).thenReturn(Optional.of(usuario));
        when(mfaService.validarDesafioYCodigo("chal-mail-enroll", "123456")).thenAnswer(inv -> {
            usuario.setTotpHabilitado(true);
            return usuario;
        });
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-enrolled");
        when(jwtService.getJwtExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.emitirRefreshToken(usuario, METADATA)).thenReturn("refresh-enrolled");
        when(mfaRecoveryService.generarCodigosRecuperacion(usuario)).thenReturn(List.of("ABCD-2345"));

        servicio.verificarTotp(new TotpVerificationRequest("chal-mail-enroll", "123456"), METADATA);

        verify(emailNotificationService).enviarCodigosRecuperacion2fa(CORREO, "Juan Garcia", List.of("ABCD-2345"));
        verify(emailNotificationService).enviarAlertaSeguridad(
                eq(CORREO), eq("Juan Garcia"), anyString(), anyString(), eq(METADATA), any(LocalDateTime.class));
    }

    private static Usuario usuarioActivo(boolean totpObligatorio, boolean totpHabilitado) {
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Juan");
        cliente.setApellidoPaterno("Garcia");
        cliente.setActivo(true);

        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCorreo(CORREO);
        usuario.setPasswordHash(HASH);
        usuario.setRol("ROLE_CLIENTE");
        usuario.setActivo(true);
        usuario.setTotpObligatorio(totpObligatorio);
        usuario.setTotpHabilitado(totpHabilitado);
        usuario.setIntentosFallidos(0);
        usuario.setTokenVersion(0L);
        usuario.setCliente(cliente);
        usuario.setFechaCreacion(LocalDateTime.now(ZoneId.systemDefault()));
        return usuario;
    }
}
