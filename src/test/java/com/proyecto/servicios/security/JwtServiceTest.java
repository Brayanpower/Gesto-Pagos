package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.seguridad.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas unitarias de JwtService")
class JwtServiceTest {

    private static final String TEST_SECRET = "test-only-jwt-signing-secret-key-minimum-32-bytes-0123456789";
    private static final String DEFAULT_ISSUER = "sistema-bancario-core";

    private JwtService jwtService;
    private Usuario usuario;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
        ReflectionTestUtils.setField(jwtService, "jwtIssuer", DEFAULT_ISSUER);

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setCorreo("cliente@banco.com");
        usuario.setPasswordHash("$2a$10$hash");
        usuario.setRol("ROLE_CLIENTE");
        usuario.setActivo(true);
        usuario.setTokenVersion(2L);
        userDetails = new CustomUserDetails(usuario);
    }

    @Test
    @DisplayName("Genera token con jti unico, iss configurado, ver del usuario y expiracion correcta")
    void generateToken_incluyeJtiIssVerYExpiracion() {
        Map<String, Object> extraClaims = Map.of("rol", "ROLE_CLIENTE");

        String token1 = jwtService.generateToken(extraClaims, userDetails);
        String token2 = jwtService.generateToken(userDetails);

        assertNotNull(token1);
        assertEquals("cliente@banco.com", jwtService.extractUsername(token1));
        assertNotNull(jwtService.extractJti(token1));
        assertFalse(jwtService.extractJti(token1).isBlank());
        assertNotEquals(jwtService.extractJti(token1), jwtService.extractJti(token2));
        assertEquals(DEFAULT_ISSUER, jwtService.extractClaim(token1, claims -> claims.getIssuer()));
        assertEquals(2L, jwtService.extractTokenVersion(token1));
        assertEquals(900L, jwtService.getJwtExpirationSeconds());
        assertTrue(jwtService.isTokenValid(token1, userDetails));
    }

    @Test
    @DisplayName("Respeta claim ver explicito en extraClaims y calcula minimo 1 segundo en getJwtExpirationSeconds")
    void generateToken_respetaVerExplicitoYMinimoExpiracion() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 500L);
        assertEquals(1L, jwtService.getJwtExpirationSeconds());

        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
        String token = jwtService.generateToken(Map.of("ver", 2L), userDetails);
        assertEquals(2L, jwtService.extractTokenVersion(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Invalida token cuando cambia tokenVersion del usuario (mismatch de ver)")
    void isTokenValid_tokenVersionMismatch_retornaFalse() {
        String token = jwtService.generateToken(userDetails);
        assertTrue(jwtService.isTokenValid(token, userDetails));

        usuario.incrementarTokenVersion();

        assertFalse(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Invalida token sin claim ver cuando UserDetails es CustomUserDetails")
    void isTokenValid_sinClaimVerParaCustomUserDetails_retornaFalse() {
        Instant now = Instant.now();
        String tokenSinVer = Jwts.builder()
                .id("jti-1")
                .issuer(DEFAULT_ISSUER)
                .subject(usuario.getCorreo())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(jwtService.extractTokenVersion(tokenSinVer));
        assertFalse(jwtService.isTokenValid(tokenSinVer, userDetails));
    }

    @Test
    @DisplayName("Invalida token expirado o con issuer distinto o usuario diferente")
    void isTokenValid_expiradoOIssuerDistintoOUsuarioDistinto_retornaFalse() {
        Instant pasado = Instant.now().minusSeconds(60);
        String tokenExpirado = Jwts.builder()
                .id("jti-exp")
                .issuer(DEFAULT_ISSUER)
                .subject(usuario.getCorreo())
                .claim("ver", 2L)
                .issuedAt(Date.from(pasado.minusSeconds(60)))
                .expiration(Date.from(pasado))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(jwtService.isTokenValid(tokenExpirado, userDetails));

        Instant ahora = Instant.now();
        String tokenOtroIssuer = Jwts.builder()
                .id("jti-iss")
                .issuer("otro-emisor")
                .subject(usuario.getCorreo())
                .claim("ver", 2L)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(jwtService.isTokenValid(tokenOtroIssuer, userDetails));

        String tokenValido = jwtService.generateToken(userDetails);
        UserDetails otroUsuario = User.withUsername("otro@banco.com").password("x").roles("CLIENTE").build();
        assertFalse(jwtService.isTokenValid(tokenValido, otroUsuario));
        assertFalse(jwtService.isTokenValid(tokenValido + "tamper", userDetails));
        assertFalse(jwtService.isTokenValid(" ", userDetails));
        assertFalse(jwtService.isTokenValid(null, userDetails));
    }

    @Test
    @DisplayName("Rechaza token sin claim iss cuando jwtIssuer esta configurado y lo permite si jwtIssuer esta vacio")
    void isTokenValid_tokenSinIssuer_exigeIssCuandoEstaConfigurado() {
        Instant ahora = Instant.now();
        String tokenSinIssuer = Jwts.builder()
                .id("jti-no-iss")
                .subject("generico@banco.com")
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        UserDetails generico = User.withUsername("generico@banco.com").password("x").roles("CLIENTE").build();
        assertFalse(jwtService.isTokenValid(tokenSinIssuer, generico));

        ReflectionTestUtils.setField(jwtService, "jwtIssuer", "");
        assertTrue(jwtService.isTokenValid(tokenSinIssuer, generico));
    }

    @Test
    @DisplayName("validarConfiguracion rechaza jwt.secret vacio o menor a 32 bytes y acepta llave robusta")
    void validarConfiguracion_validaLongitudMinimaDeLlave() {
        JwtService vacio = new JwtService();
        ReflectionTestUtils.setField(vacio, "secretKey", "  ");
        assertThrows(IllegalStateException.class, vacio::validarConfiguracion);

        JwtService corto = new JwtService();
        ReflectionTestUtils.setField(corto, "secretKey", "llave-demasiado-corta");
        assertThrows(IllegalStateException.class, corto::validarConfiguracion);

        jwtService.validarConfiguracion();
    }

    @Test
    @DisplayName("Inyecta valores @Value por defecto y personalizados en contexto Spring")
    void springContext_inyectaPropiedadesJwtCorrectamente() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("jwt-props", Map.of(
                    "jwt.secret", TEST_SECRET,
                    "jwt.expiration", "120000",
                    "jwt.issuer", "banca-custom"
            )));
            ctx.register(JwtService.class);
            ctx.refresh();

            JwtService bean = ctx.getBean(JwtService.class);
            assertEquals(120L, bean.getJwtExpirationSeconds());
            String token = bean.generateToken(userDetails);
            assertEquals("banca-custom", bean.extractClaim(token, claims -> claims.getIssuer()));
            assertTrue(bean.isTokenValid(token, userDetails));
        }
    }
}
