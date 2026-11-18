package com.proyecto.servicios.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de TotpService")
class TotpServiceTest {

    // Secreto oficial RFC 6238 Appendix B ("12345678901234567890" en ASCII)
    private static final String RFC_6238_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final long INSTANTE_BASE = 1234567890L;
    private static final long PASO_BASE = INSTANTE_BASE / 30L;
    private static final String CODIGO_BASE = "005924";

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @ParameterizedTest
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037"
    })
    @DisplayName("Genera codigos exactos para vectores oficiales RFC 6238 Appendix B (SHA1)")
    void generarCodigoEnInstante_vectoresRfc6238_coincideExactamente(long epochSeconds, String codigoEsperado) {
        String codigo = totpService.generarCodigoEnInstante(RFC_6238_SECRET_BASE32, epochSeconds);

        assertEquals(codigoEsperado, codigo);
    }

    @Test
    @DisplayName("Decodifica secreto Base32 con minusculas, espacios y padding '='")
    void generarCodigoEnInstante_secretoConFormatoFlexible_generaMismoCodigo() {
        String secretoFlexible = " gezdgnbv gy3tqojq gezdgnbv gy3tqojq==== ";

        String codigo = totpService.generarCodigoEnInstante(secretoFlexible, 59L);

        assertEquals("287082", codigo);
    }

    @Test
    @DisplayName("Genera secreto Base32 valido de 32 caracteres sin padding")
    void generarSecretoBase32_genera32CaracteresBase32SinPadding() {
        String secreto1 = totpService.generarSecretoBase32();
        String secreto2 = totpService.generarSecretoBase32();

        assertNotNull(secreto1);
        assertEquals(32, secreto1.length());
        assertTrue(secreto1.matches("^[A-Z2-7]{32}$"));
        assertFalse(secreto1.contains("="));
        assertNotEquals(secreto1, secreto2);

        String codigoActual = totpService.generarCodigoActual(secreto1);
        assertTrue(codigoActual.matches("^\\d{6}$"));
        assertTrue(totpService.verificarCodigoConPaso(secreto1, codigoActual, null).isPresent());
    }

    @Test
    @DisplayName("Construye URI otpauth://totp valida con issuer por defecto y parametros RFC")
    void construirOtpAuthUri_issuerPorDefecto_formatoValido() {
        String uriText = totpService.construirOtpAuthUri("usuario@banco.com", RFC_6238_SECRET_BASE32);

        assertTrue(uriText.startsWith("otpauth://totp/SistemaBancario:usuario%40banco.com?"));
        assertTrue(uriText.contains("secret=" + RFC_6238_SECRET_BASE32));
        assertTrue(uriText.contains("issuer=SistemaBancario"));
        assertTrue(uriText.contains("algorithm=SHA1"));
        assertTrue(uriText.contains("digits=6"));
        assertTrue(uriText.contains("period=30"));

        URI uri = URI.create(uriText);
        assertEquals("otpauth", uri.getScheme());
        assertEquals("totp", uri.getHost());
    }

    @Test
    @DisplayName("Construye URI otpauth:// reemplazando '+' por '%20' cuando issuer tiene espacios")
    void construirOtpAuthUri_issuerConEspacios_codificaEspaciosComoPorcentaje20() {
        ReflectionTestUtils.setField(totpService, "totpIssuer", "Sistema Bancario MX");

        String uriText = totpService.construirOtpAuthUri("cliente+vip@correo.com", RFC_6238_SECRET_BASE32);

        assertFalse(uriText.contains("+"));
        assertTrue(uriText.startsWith("otpauth://totp/Sistema%20Bancario%20MX:cliente%2Bvip%40correo.com?"));
        assertTrue(uriText.contains("issuer=Sistema%20Bancario%20MX"));
    }

    @Test
    @DisplayName("Inyecta propiedad app.auth.totp.issuer via Spring @Value con valor por defecto y personalizado")
    void springContext_inyectaTotpIssuerPorDefectoYPersonalizado() {
        try (AnnotationConfigApplicationContext defaultCtx = new AnnotationConfigApplicationContext()) {
            defaultCtx.register(TotpService.class);
            defaultCtx.refresh();
            TotpService beanDefault = defaultCtx.getBean(TotpService.class);
            String uriDefault = beanDefault.construirOtpAuthUri("user@banco.com", RFC_6238_SECRET_BASE32);
            assertTrue(uriDefault.contains("issuer=SistemaBancario"));
        }

        try (AnnotationConfigApplicationContext customCtx = new AnnotationConfigApplicationContext()) {
            customCtx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("totp-props", Map.of("app.auth.totp.issuer", "Banca Digital")));
            customCtx.register(TotpService.class);
            customCtx.refresh();
            TotpService beanCustom = customCtx.getBean(TotpService.class);
            String uriCustom = beanCustom.construirOtpAuthUri("user@banco.com", RFC_6238_SECRET_BASE32);
            assertTrue(uriCustom.contains("issuer=Banca%20Digital"));
        }
    }

    @Test
    @DisplayName("Acepta codigo exacto y dentro de ventana de deriva +-30 segundos")
    void verificarCodigoEnInstante_dentroDeVentana_aceptaYDevuelvePasoCoincidente() {
        Optional<Long> exacto = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE, null);
        Optional<Long> derivaMenos30 = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE + 30L, null);
        Optional<Long> derivaMas30 = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE - 30L, null);

        assertEquals(Optional.of(PASO_BASE), exacto);
        assertEquals(Optional.of(PASO_BASE), derivaMenos30);
        assertEquals(Optional.of(PASO_BASE), derivaMas30);
    }

    @Test
    @DisplayName("Rechaza codigo fuera de ventana (+60 segundos y -60 segundos)")
    void verificarCodigoEnInstante_fueraDeVentana60Segundos_rechaza() {
        Optional<Long> pasado60 = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE + 60L, null);
        Optional<Long> futuro60 = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE - 60L, null);

        assertTrue(pasado60.isEmpty());
        assertTrue(futuro60.isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "12345", "1234567", "12345a", "abcdef", " 005924", "005924 ", "١٢٣٤٥٦"})
    @DisplayName("Rechaza codigos con formato invalido distinto a 6 digitos ASCII")
    void verificarCodigoEnInstante_formatoInvalido_devuelveEmpty(String codigoInvalido) {
        Optional<Long> resultado = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, codigoInvalido, INSTANTE_BASE, null);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Rechaza codigo valido pero incorrecto y secreto nulo o blanco en verificacion")
    void verificarCodigoEnInstante_codigoIncorrectoOSecretoVacio_devuelveEmpty() {
        assertTrue(totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, "999999", INSTANTE_BASE, null).isEmpty());
        assertTrue(totpService.verificarCodigoEnInstante(
                null, CODIGO_BASE, INSTANTE_BASE, null).isEmpty());
        assertTrue(totpService.verificarCodigoEnInstante(
                "   ", CODIGO_BASE, INSTANTE_BASE, null).isEmpty());
    }

    @Test
    @DisplayName("Rechaza anti-replay cuando ultimoPasoUsado >= pasoCoincidente")
    void verificarCodigoEnInstante_antiReplay_rechazaCuandoUltimoPasoEsMayorOIgual() {
        Optional<Long> mismoPaso = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE, PASO_BASE);
        Optional<Long> pasoPosterior = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE, PASO_BASE + 1L);
        Optional<Long> derivaReusada = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE + 30L, PASO_BASE);
        Optional<Long> pasoAnteriorPermitido = totpService.verificarCodigoEnInstante(
                RFC_6238_SECRET_BASE32, CODIGO_BASE, INSTANTE_BASE, PASO_BASE - 1L);

        assertTrue(mismoPaso.isEmpty());
        assertTrue(pasoPosterior.isEmpty());
        assertTrue(derivaReusada.isEmpty());
        assertEquals(Optional.of(PASO_BASE), pasoAnteriorPermitido);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "INVALID0189", "GEZD=GNBV", "A", "===="})
    @DisplayName("Lanza IllegalArgumentException cuando el secreto Base32 es invalido")
    void generarCodigoEnInstante_secretoInvalido_lanzaIllegalArgumentException(String secretoInvalido) {
        assertThrows(IllegalArgumentException.class,
                () -> totpService.generarCodigoEnInstante(secretoInvalido, INSTANTE_BASE));
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException en construirOtpAuthUri con correo nulo o vacio")
    void construirOtpAuthUri_correoInvalido_lanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> totpService.construirOtpAuthUri(null, RFC_6238_SECRET_BASE32));
        assertThrows(IllegalArgumentException.class,
                () -> totpService.construirOtpAuthUri("   ", RFC_6238_SECRET_BASE32));
    }
}
