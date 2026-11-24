package com.proyecto.servicios.model.seguridad;

import com.proyecto.servicios.entity.seguridad.AuditoriaLogin;
import com.proyecto.servicios.entity.seguridad.MfaChallenge;
import com.proyecto.servicios.entity.seguridad.RefreshToken;
import com.proyecto.servicios.entity.seguridad.TipoDesafioMfa;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.CuentaBloqueadaException;
import com.proyecto.servicios.exception.RateLimitExceededException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validaciones y contratos de seguridad de login (DTOs, entidades y excepciones)")
class AuthSecurityModelsValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("LoginRequest con constructor de 2 argumentos es valido sin codigoTotp")
    void loginRequest_dosArgumentos_sinViolaciones() {
        LoginRequest request = new LoginRequest("cliente@banco.com", "Banco123@");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("LoginRequest acepta codigoTotp de 6 digitos y rechaza formato invalido o longitudes excedidas")
    void loginRequest_validacionesCampos() {
        LoginRequest valido = new LoginRequest("cliente@banco.com", "Banco123@", "123456");
        assertTrue(validator.validate(valido).isEmpty());

        LoginRequest otpInvalido = new LoginRequest("cliente@banco.com", "Banco123@", "12ab56");
        assertTrue(propiedadesConError(otpInvalido).contains("codigoTotp"));

        LoginRequest correoLargo = new LoginRequest("a".repeat(95) + "@banco.com", "Banco123@");
        assertTrue(propiedadesConError(correoLargo).contains("correo"));

        LoginRequest passwordLargo = new LoginRequest("cliente@banco.com", "x".repeat(129));
        assertTrue(propiedadesConError(passwordLargo).contains("password"));
    }

    @Test
    @DisplayName("TotpVerificationRequest valida challengeToken obligatorio y codigo de 6 digitos")
    void totpVerificationRequest_validaciones() {
        TotpVerificationRequest valido = new TotpVerificationRequest("challenge-token-abc", "004321");
        assertTrue(validator.validate(valido).isEmpty());

        TotpVerificationRequest invalido = new TotpVerificationRequest("  ", "123");
        Set<String> errores = propiedadesConError(invalido);
        assertTrue(errores.contains("challengeToken"));
        assertTrue(errores.contains("codigo"));
    }

    @Test
    @DisplayName("RefreshTokenRequest valida @NotBlank y longitud entre 20 y 512")
    void refreshTokenRequest_validaciones() {
        RefreshTokenRequest valido = new RefreshTokenRequest("a".repeat(20));
        assertTrue(validator.validate(valido).isEmpty());

        RefreshTokenRequest corto = new RefreshTokenRequest("corto");
        assertTrue(propiedadesConError(corto).contains("refreshToken"));

        RefreshTokenRequest largo = new RefreshTokenRequest("a".repeat(513));
        assertTrue(propiedadesConError(largo).contains("refreshToken"));
    }

    @Test
    @DisplayName("AuthResponse conserva constructor de 5 argumentos y expone campos MFA/refresh")
    void authResponse_compatibilidadYNuevosCampos() {
        AuthResponse legacy = new AuthResponse("jwt", "user@banco.com", "ROLE_CLIENTE", 1L, "Juan Garcia");
        assertEquals("jwt", legacy.getToken());
        assertEquals("Bearer", legacy.getTipoToken());
        assertEquals(0, legacy.getCodigo());

        legacy.setRefreshToken("refresh-12345678901234567890");
        legacy.setExpiresInSegundos(900L);
        legacy.setRequiereTotp(true);
        legacy.setTotpConfiguracionRequerida(true);
        legacy.setChallengeToken("chal-123");
        legacy.setTotpSecret("BASE32SECRET");
        legacy.setOtpauthUri("otpauth://totp/SistemaBancario:user@banco.com?secret=BASE32SECRET");

        assertEquals(900L, legacy.getExpiresInSegundos());
        assertTrue(legacy.getRequiereTotp());
        assertTrue(legacy.getTotpConfiguracionRequerida());
    }

    @Test
    @DisplayName("ClientMetadata normaliza null/blank y recorta a 64 y 255 caracteres")
    void clientMetadata_normalizacionYRecorte() {
        ClientMetadata desconocido = ClientMetadata.desconocido();
        assertNull(desconocido.ipOrigen());
        assertNull(desconocido.userAgent());

        ClientMetadata blank = ClientMetadata.of("   ", "");
        assertNull(blank.ipOrigen());
        assertNull(blank.userAgent());

        ClientMetadata recortado = ClientMetadata.of(" " + "1".repeat(80) + " ", " " + "U".repeat(300));
        assertEquals(64, recortado.ipOrigen().length());
        assertEquals(255, recortado.userAgent().length());
    }

    @Test
    @DisplayName("Usuario tiene valores por defecto, incrementa tokenVersion y ejecuta prePersist")
    void usuario_defaultsYPrePersist() {
        Usuario usuario = new Usuario();
        assertTrue(usuario.isTotpObligatorio());
        assertFalse(usuario.isTotpHabilitado());
        assertEquals(0, usuario.getIntentosFallidos());
        assertEquals(0L, usuario.getTokenVersion());

        usuario.incrementarTokenVersion();
        assertEquals(1L, usuario.getTokenVersion());

        usuario.setIntentosFallidos(null);
        usuario.setTokenVersion(null);
        usuario.prePersist();

        assertNotNull(usuario.getFechaCreacion());
        assertEquals(0, usuario.getIntentosFallidos());
        assertEquals(0L, usuario.getTokenVersion());
        assertEquals("ROLE_CLIENTE", usuario.getRol());
        assertTrue(usuario.isActivo());
    }

    @Test
    @DisplayName("RefreshToken, MfaChallenge y AuditoriaLogin inicializan prePersist correctamente")
    void nuevasEntidades_prePersist() {
        RefreshToken rt = new RefreshToken();
        rt.prePersist();
        assertNotNull(rt.getFechaCreacion());
        assertFalse(rt.isRevocado());

        MfaChallenge challenge = new MfaChallenge();
        challenge.setTipo(TipoDesafioMfa.ENROLLMENT);
        challenge.setIntentos(null);
        challenge.prePersist();
        assertNotNull(challenge.getFechaCreacion());
        assertEquals(0, challenge.getIntentos());
        assertFalse(challenge.isConsumido());

        AuditoriaLogin auditoria = new AuditoriaLogin();
        auditoria.prePersist();
        assertNotNull(auditoria.getFechaCreacion());

        AuditoriaLoginResponse auditDto = AuditoriaLoginResponse.builder()
                .id(1L)
                .usuarioId(2L)
                .correo("user@banco.com")
                .evento("LOGIN_OK")
                .exitoso(true)
                .fechaCreacion(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        assertTrue(auditDto.isExitoso());
        assertEquals("LOGIN_OK", auditDto.getEvento());
    }

    @Test
    @DisplayName("Excepciones CuentaBloqueadaException y RateLimitExceededException exponen mensaje y retryAfterSeconds")
    void excepciones_propiedades() {
        CuentaBloqueadaException bloqueada = new CuentaBloqueadaException("Cuenta bloqueada temporalmente");
        assertEquals("Cuenta bloqueada temporalmente", bloqueada.getMessage());

        RateLimitExceededException rateLimit = new RateLimitExceededException("Demasiados intentos", 45L);
        assertEquals("Demasiados intentos", rateLimit.getMessage());
        assertEquals(45L, rateLimit.getRetryAfterSeconds());
    }

    private <T> Set<String> propiedadesConError(T request) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(request);
        assertFalse(violaciones.isEmpty(), "Se esperaban violaciones de validacion");
        return violaciones.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
