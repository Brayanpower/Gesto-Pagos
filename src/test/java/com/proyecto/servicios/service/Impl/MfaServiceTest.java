package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.MfaChallenge;
import com.proyecto.servicios.entity.seguridad.TipoDesafioMfa;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.MfaChallengeRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.TotpService;
import com.proyecto.servicios.service.EncryptionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de MfaService")
class MfaServiceTest {

    private static final String BASE32_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String OTPAUTH_URI =
            "otpauth://totp/SistemaBancario:usuario%40banco.com?secret=" + BASE32_SECRET + "&issuer=SistemaBancario";

    @Mock private TotpService totpService;
    @Mock private EncryptionHelper encryptionHelper;
    @Mock private MfaChallengeRepository mfaChallengeRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private MfaService mfaService;

    @Test
    @DisplayName("Iniciar desafio para usuario sin TOTP habilitado genera secreto cifrado ENC: y desafio ENROLLMENT")
    void iniciarDesafio_sinTotpHabilitado_creaEnrollmentYCifraSecreto() throws Exception {
        Usuario usuario = crearUsuario(10L, false, null);
        ClientMetadata metadata = ClientMetadata.of("198.51.100.10", "Mozilla/5.0");

        when(totpService.generarSecretoBase32()).thenReturn(BASE32_SECRET);
        when(encryptionHelper.encrypt(BASE32_SECRET)).thenReturn("cipher-b64");
        when(totpService.construirOtpAuthUri("usuario@banco.com", BASE32_SECRET)).thenReturn(OTPAUTH_URI);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mfaChallengeRepository.save(any(MfaChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        MfaService.ChallengeInitResult result = mfaService.iniciarDesafio(usuario, metadata);

        assertNotNull(result.challengeToken());
        assertTrue(result.configuracionRequerida());
        assertEquals(BASE32_SECRET, result.totpSecret());
        assertEquals(OTPAUTH_URI, result.otpauthUri());
        assertEquals("ENC:cipher-b64", usuario.getTotpSecretEnc());

        verify(mfaChallengeRepository).invalidarActivosPorUsuarioId(10L);
        verify(usuarioRepository).save(usuario);

        ArgumentCaptor<MfaChallenge> challengeCaptor = ArgumentCaptor.forClass(MfaChallenge.class);
        verify(mfaChallengeRepository).save(challengeCaptor.capture());
        MfaChallenge guardado = challengeCaptor.getValue();
        assertEquals(TipoDesafioMfa.ENROLLMENT, guardado.getTipo());
        assertEquals(sha256Hex(result.challengeToken()), guardado.getChallengeHash());
        assertNotEquals(result.challengeToken(), guardado.getChallengeHash());
        assertEquals(0, guardado.getIntentos());
        assertFalse(guardado.isConsumido());
        assertEquals("198.51.100.10", guardado.getIpOrigen());
        assertEquals("Mozilla/5.0", guardado.getUserAgent());
        assertTrue(guardado.getExpiraEn().isAfter(LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(4)));
    }

    @Test
    @DisplayName("Iniciar desafio para usuario con TOTP habilitado crea desafio VERIFICATION sin exponer secreto")
    void iniciarDesafio_conTotpHabilitado_creaVerificationSinSecreto() throws Exception {
        Usuario usuario = crearUsuario(11L, true, "ENC:cipher-existente");
        when(mfaChallengeRepository.save(any(MfaChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        MfaService.ChallengeInitResult result = mfaService.iniciarDesafio(usuario, ClientMetadata.desconocido());

        assertNotNull(result.challengeToken());
        assertFalse(result.configuracionRequerida());
        assertNull(result.totpSecret());
        assertNull(result.otpauthUri());

        verify(mfaChallengeRepository).invalidarActivosPorUsuarioId(11L);
        verify(totpService, never()).generarSecretoBase32();
        verify(usuarioRepository, never()).save(any());

        ArgumentCaptor<MfaChallenge> captor = ArgumentCaptor.forClass(MfaChallenge.class);
        verify(mfaChallengeRepository).save(captor.capture());
        assertEquals(TipoDesafioMfa.VERIFICATION, captor.getValue().getTipo());
        assertEquals(sha256Hex(result.challengeToken()), captor.getValue().getChallengeHash());
    }

    @Test
    @DisplayName("Validar desafio ENROLLMENT con codigo correcto habilita TOTP, actualiza paso y consume desafio")
    void validarDesafioYCodigo_enrollmentValido_habilitaTotpYConsume() throws Exception {
        String rawToken = "challenge-token-enrollment";
        Usuario usuario = crearUsuario(10L, false, "ENC:cipher-b64");
        MfaChallenge challenge = crearChallenge(usuario, sha256Hex(rawToken), TipoDesafioMfa.ENROLLMENT, 0, false,
                LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(5));

        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(challenge));
        when(encryptionHelper.decryptToString("cipher-b64")).thenReturn(BASE32_SECRET);
        when(totpService.verificarCodigoConPaso(BASE32_SECRET, "123456", null)).thenReturn(Optional.of(42L));

        Usuario resultado = mfaService.validarDesafioYCodigo(rawToken, "123456");

        assertEquals(usuario, resultado);
        assertTrue(usuario.isTotpHabilitado());
        assertNotNull(usuario.getTotpConfiguradoEn());
        assertEquals(42L, usuario.getTotpUltimoPaso());
        assertTrue(challenge.isConsumido());
        verify(usuarioRepository).save(usuario);
        verify(mfaChallengeRepository).save(challenge);
    }

    @Test
    @DisplayName("Validar desafio VERIFICATION soporta secreto sin prefijo ENC: tanto cifrado como texto plano")
    void validarDesafioYCodigo_sinPrefijoEnc_soportaFallback() throws Exception {
        String rawToken = "challenge-token-plain";
        Usuario usuario = crearUsuario(10L, true, BASE32_SECRET);
        usuario.setTotpUltimoPaso(40L);
        MfaChallenge challenge = crearChallenge(usuario, sha256Hex(rawToken), TipoDesafioMfa.VERIFICATION, 0, false,
                LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(5));

        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(challenge));
        when(encryptionHelper.decryptToString(BASE32_SECRET)).thenThrow(new IllegalStateException("No cifrado"));
        when(totpService.verificarCodigoConPaso(BASE32_SECRET, "654321", 40L)).thenReturn(Optional.of(41L));

        Usuario resultado = mfaService.validarDesafioYCodigo(rawToken, "654321");

        assertEquals(41L, resultado.getTotpUltimoPaso());
        assertTrue(challenge.isConsumido());
    }

    @Test
    @DisplayName("Validar desafio con codigo TOTP invalido incrementa intentos y consume al alcanzar el maximo")
    void validarDesafioYCodigo_codigoInvalido_incrementaIntentosYConsumeEnMaximo() throws Exception {
        String rawToken = "challenge-token-fail";
        Usuario usuario = crearUsuario(10L, true, "ENC:cipher-b64");
        MfaChallenge challenge = crearChallenge(usuario, sha256Hex(rawToken), TipoDesafioMfa.VERIFICATION, 2, false,
                LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(5));

        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(challenge));
        when(encryptionHelper.decryptToString("cipher-b64")).thenReturn(BASE32_SECRET);
        when(totpService.verificarCodigoConPaso(BASE32_SECRET, "000000", null)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> mfaService.validarDesafioYCodigo(rawToken, "000000"));

        assertEquals(3, challenge.getIntentos());
        assertTrue(challenge.isConsumido());
        verify(mfaChallengeRepository).save(challenge);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validar desafio rechazado cuando esta consumido, expirado, sin intentos o no existe")
    void validarDesafioYCodigo_estadosInvalidos_lanzaBadCredentials() throws Exception {
        assertThrows(BadCredentialsException.class, () -> mfaService.validarDesafioYCodigo("  ", "123456"));

        String hash = sha256Hex("missing-token");
        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(hash)).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> mfaService.validarDesafioYCodigo("missing-token", "123456"));

        Usuario usuario = crearUsuario(10L, true, "ENC:cipher");
        MfaChallenge consumido = crearChallenge(usuario, sha256Hex("used"), TipoDesafioMfa.VERIFICATION, 0, true,
                LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(5));
        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex("used")))
                .thenReturn(Optional.of(consumido));
        assertThrows(BadCredentialsException.class, () -> mfaService.validarDesafioYCodigo("used", "123456"));

        MfaChallenge expirado = crearChallenge(usuario, sha256Hex("expired"), TipoDesafioMfa.VERIFICATION, 0, false,
                LocalDateTime.now(ZoneId.systemDefault()).minusSeconds(1));
        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex("expired")))
                .thenReturn(Optional.of(expirado));
        assertThrows(BadCredentialsException.class, () -> mfaService.validarDesafioYCodigo("expired", "123456"));

        MfaChallenge agotado = crearChallenge(usuario, sha256Hex("maxed"), TipoDesafioMfa.VERIFICATION, 3, false,
                LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(5));
        when(mfaChallengeRepository.findWithUsuarioByChallengeHash(sha256Hex("maxed")))
                .thenReturn(Optional.of(agotado));
        assertThrows(BadCredentialsException.class, () -> mfaService.validarDesafioYCodigo("maxed", "123456"));
    }

    @Test
    @DisplayName("Validar codigo directo en login actualiza totpUltimoPaso cuando es valido y falla si es invalido")
    void validarCodigoDirectoEnLogin_validoEInvalido() {
        Usuario usuario = crearUsuario(10L, true, "ENC:cipher-b64");
        usuario.setTotpUltimoPaso(99L);

        when(encryptionHelper.decryptToString("cipher-b64")).thenReturn(BASE32_SECRET);
        when(totpService.verificarCodigoConPaso(BASE32_SECRET, "123456", 99L)).thenReturn(Optional.of(100L));
        when(totpService.verificarCodigoConPaso(BASE32_SECRET, "999999", 100L)).thenReturn(Optional.empty());

        mfaService.validarCodigoDirectoEnLogin(usuario, "123456");
        assertEquals(100L, usuario.getTotpUltimoPaso());
        verify(usuarioRepository).save(usuario);

        assertThrows(BadCredentialsException.class,
                () -> mfaService.validarCodigoDirectoEnLogin(usuario, "999999"));
        verify(totpService, never()).generarSecretoBase32();
        verify(encryptionHelper, times(2)).decryptToString("cipher-b64");
        verify(usuarioRepository, times(1)).save(usuario);
    }

    private Usuario crearUsuario(Long id, boolean totpHabilitado, String totpSecretEnc) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo("usuario@banco.com");
        usuario.setActivo(true);
        usuario.setTotpObligatorio(true);
        usuario.setTotpHabilitado(totpHabilitado);
        usuario.setTotpSecretEnc(totpSecretEnc);
        return usuario;
    }

    private MfaChallenge crearChallenge(
            Usuario usuario,
            String challengeHash,
            TipoDesafioMfa tipo,
            int intentos,
            boolean consumido,
            LocalDateTime expiraEn) {
        MfaChallenge challenge = new MfaChallenge();
        challenge.setId(1L);
        challenge.setUsuario(usuario);
        challenge.setChallengeHash(challengeHash);
        challenge.setTipo(tipo);
        challenge.setIntentos(intentos);
        challenge.setConsumido(consumido);
        challenge.setExpiraEn(expiraEn);
        return challenge;
    }

    private String sha256Hex(String valor) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
    }
}
