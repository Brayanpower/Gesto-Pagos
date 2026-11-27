package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.MfaChallenge;
import com.proyecto.servicios.entity.seguridad.TipoDesafioMfa;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.MfaChallengeRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.TotpService;
import com.proyecto.servicios.service.EncryptionHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class MfaService {

    private static final String ENC_PREFIX = "ENC:";
    private static final String MSG_DESAFIO_INVALIDO = "Desafio MFA invalido o expirado";
    private static final String MSG_CODIGO_TOTP_INVALIDO = "Codigo de verificacion TOTP invalido";
    private static final String MSG_CODIGO_RECUPERACION_INVALIDO = "Codigo de recuperacion por correo invalido o expirado";

    private final TotpService totpService;
    private final EncryptionHelper encryptionHelper;
    private final MfaChallengeRepository mfaChallengeRepository;
    private final UsuarioRepository usuarioRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.totp.challenge-minutes:5}")
    private long challengeMinutes = 5L;

    @Value("${app.auth.totp.max-attempts:3}")
    private int maxAttempts = 3;

    @Value("${app.auth.email-otp.expiration-minutes:5}")
    private long emailOtpExpirationMinutes = 5L;

    @Value("${app.auth.email-otp.cooldown-seconds:60}")
    private long emailOtpCooldownSeconds = 60L;

    public MfaService(
            TotpService totpService,
            EncryptionHelper encryptionHelper,
            MfaChallengeRepository mfaChallengeRepository,
            UsuarioRepository usuarioRepository) {
        this.totpService = totpService;
        this.encryptionHelper = encryptionHelper;
        this.mfaChallengeRepository = mfaChallengeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public record ChallengeInitResult(
            String challengeToken,
            boolean configuracionRequerida,
            String totpSecret,
            String otpauthUri) {
    }

    public record EmailOtpIssueResult(
            Usuario usuario,
            String codigoOtp,
            long minutosVigencia) {
    }

    public record EmailRecoveryInitResult(
            String challengeToken,
            Usuario usuario,
            String codigoOtp,
            long minutosVigencia) {
    }

    /**
     * Invalida desafios activos previos del usuario e inicia un nuevo desafio MFA
     * (ENROLLMENT en el primer ingreso o VERIFICATION cuando TOTP ya esta configurado).
     */
    @Transactional
    public ChallengeInitResult iniciarDesafio(Usuario usuario, ClientMetadata metadata) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException("El usuario es obligatorio para iniciar un desafio MFA");
        }
        mfaChallengeRepository.invalidarActivosPorUsuarioId(usuario.getId());

        String rawChallengeToken = UUID.randomUUID().toString();
        String challengeHash = sha256Hex(rawChallengeToken);
        ClientMetadata meta = metadata != null ? metadata : ClientMetadata.desconocido();

        if (!usuario.isTotpHabilitado()) {
            String secret = totpService.generarSecretoBase32();
            usuario.setTotpSecretEnc(ENC_PREFIX + encryptionHelper.encrypt(secret));
            usuarioRepository.save(usuario);

            String otpauthUri = totpService.construirOtpAuthUri(usuario.getCorreo(), secret);
            MfaChallenge challenge = construirChallenge(usuario, challengeHash, TipoDesafioMfa.ENROLLMENT, meta);
            mfaChallengeRepository.save(challenge);
            return new ChallengeInitResult(rawChallengeToken, true, secret, otpauthUri);
        }

        MfaChallenge challenge = construirChallenge(usuario, challengeHash, TipoDesafioMfa.VERIFICATION, meta);
        mfaChallengeRepository.save(challenge);
        return new ChallengeInitResult(rawChallengeToken, false, null, null);
    }

    /**
     * Genera y almacena un codigo OTP de un solo uso por correo para un desafio MFA activo de login.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, ReglaNegocioException.class})
    public EmailOtpIssueResult generarYGuardarOtpCorreo(String rawChallengeToken) {
        if (rawChallengeToken == null || rawChallengeToken.isBlank()) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }
        String challengeHash = sha256Hex(rawChallengeToken);
        MfaChallenge challenge = mfaChallengeRepository.findWithUsuarioByChallengeHash(challengeHash)
                .orElseThrow(() -> new BadCredentialsException(MSG_DESAFIO_INVALIDO));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int limiteIntentos = Math.max(1, maxAttempts);
        int intentosActuales = challenge.getIntentos() == null ? 0 : challenge.getIntentos();

        if (!esDesafioVigente(challenge, now, intentosActuales, limiteIntentos)
                || challenge.getTipo() == TipoDesafioMfa.EMAIL_RECOVERY
                || challenge.getUsuario() == null) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }

        long cooldown = Math.max(1L, emailOtpCooldownSeconds);
        if (challenge.getEmailOtpEnviadoEn() != null
                && challenge.getEmailOtpEnviadoEn().plusSeconds(cooldown).isAfter(now)) {
            throw new ReglaNegocioException("Debe esperar unos segundos antes de solicitar un nuevo codigo por correo.");
        }

        long vigencia = Math.max(1L, emailOtpExpirationMinutes);
        String codigoOtp = generarCodigoOtpSeisDigitos();
        challenge.setEmailOtpHash(sha256Hex(codigoOtp));
        challenge.setEmailOtpExpiraEn(now.plusMinutes(vigencia));
        challenge.setEmailOtpEnviadoEn(now);
        mfaChallengeRepository.save(challenge);

        return new EmailOtpIssueResult(challenge.getUsuario(), codigoOtp, vigencia);
    }

    /**
     * Inicia un desafio exclusivo de recuperacion de acceso 2FA mediante codigo OTP por correo.
     */
    @Transactional
    public EmailRecoveryInitResult iniciarDesafioRecuperacionCorreo(Usuario usuario, ClientMetadata metadata) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException("El usuario es obligatorio para iniciar recuperacion por correo");
        }
        mfaChallengeRepository.invalidarActivosPorUsuarioId(usuario.getId());

        String rawChallengeToken = UUID.randomUUID().toString();
        String challengeHash = sha256Hex(rawChallengeToken);
        ClientMetadata meta = metadata != null ? metadata : ClientMetadata.desconocido();

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        long vigencia = Math.max(1L, emailOtpExpirationMinutes);
        String codigoOtp = generarCodigoOtpSeisDigitos();

        MfaChallenge challenge = construirChallenge(usuario, challengeHash, TipoDesafioMfa.EMAIL_RECOVERY, meta);
        challenge.setEmailOtpHash(sha256Hex(codigoOtp));
        challenge.setEmailOtpExpiraEn(now.plusMinutes(vigencia));
        challenge.setEmailOtpEnviadoEn(now);
        mfaChallengeRepository.save(challenge);

        return new EmailRecoveryInitResult(rawChallengeToken, usuario, codigoOtp, vigencia);
    }

    /**
     * Valida un desafio exclusivo EMAIL_RECOVERY contra el codigo OTP temporal enviado por correo.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public Usuario validarDesafioRecuperacionCorreo(String rawChallengeToken, String codigo) {
        if (rawChallengeToken == null || rawChallengeToken.isBlank()) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }
        String challengeHash = sha256Hex(rawChallengeToken);
        MfaChallenge challenge = mfaChallengeRepository.findWithUsuarioByChallengeHash(challengeHash)
                .orElseThrow(() -> new BadCredentialsException(MSG_DESAFIO_INVALIDO));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int limiteIntentos = Math.max(1, maxAttempts);
        int intentosActuales = challenge.getIntentos() == null ? 0 : challenge.getIntentos();

        if (!esDesafioVigente(challenge, now, intentosActuales, limiteIntentos)
                || challenge.getTipo() != TipoDesafioMfa.EMAIL_RECOVERY
                || !esOtpCorreoVigente(challenge, now)
                || challenge.getUsuario() == null) {
            throw new BadCredentialsException(MSG_CODIGO_RECUPERACION_INVALIDO);
        }

        if (!coincideOtpCorreo(codigo, challenge.getEmailOtpHash())) {
            registrarIntentoFallidoChallenge(challenge, intentosActuales, limiteIntentos);
            throw new BadCredentialsException(MSG_CODIGO_RECUPERACION_INVALIDO);
        }

        challenge.setConsumido(true);
        challenge.setEmailOtpHash(null);
        mfaChallengeRepository.save(challenge);
        return challenge.getUsuario();
    }

    /**
     * Valida un token de desafio MFA junto con el codigo OTP de 6 digitos (correo o TOTP).
     * Incrementa intentos fallidos y consume el desafio si supera el limite o tiene exito.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public Usuario validarDesafioYCodigo(String rawChallengeToken, String codigoTotp) {
        if (rawChallengeToken == null || rawChallengeToken.isBlank()) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }
        String challengeHash = sha256Hex(rawChallengeToken);
        MfaChallenge challenge = mfaChallengeRepository.findWithUsuarioByChallengeHash(challengeHash)
                .orElseThrow(() -> new BadCredentialsException(MSG_DESAFIO_INVALIDO));

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        int limiteIntentos = Math.max(1, maxAttempts);
        int intentosActuales = challenge.getIntentos() == null ? 0 : challenge.getIntentos();

        if (!esDesafioVigente(challenge, now, intentosActuales, limiteIntentos)
                || challenge.getTipo() == TipoDesafioMfa.EMAIL_RECOVERY) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }

        Usuario usuario = challenge.getUsuario();
        if (usuario == null) {
            throw new BadCredentialsException(MSG_DESAFIO_INVALIDO);
        }

        if (esOtpCorreoVigente(challenge, now) && coincideOtpCorreo(codigoTotp, challenge.getEmailOtpHash())) {
            challenge.setConsumido(true);
            challenge.setEmailOtpHash(null);
            mfaChallengeRepository.save(challenge);
            return usuario;
        }

        String secret = descifrarSecretoTotp(usuario.getTotpSecretEnc());
        Optional<Long> pasoValido = verificarPasoTotp(secret, codigoTotp, usuario.getTotpUltimoPaso());
        if (pasoValido.isEmpty()) {
            registrarIntentoFallidoChallenge(challenge, intentosActuales, limiteIntentos);
            throw new BadCredentialsException(MSG_CODIGO_TOTP_INVALIDO);
        }

        aplicarVerificacionExitosa(usuario, challenge, pasoValido.get(), now);
        return usuario;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarUsuarioPorChallengeToken(String rawChallengeToken) {
        if (rawChallengeToken == null || rawChallengeToken.isBlank()) {
            return Optional.empty();
        }
        String challengeHash = sha256Hex(rawChallengeToken);
        return mfaChallengeRepository.findWithUsuarioByChallengeHash(challengeHash)
                .map(MfaChallenge::getUsuario);
    }

    /**
     * Valida directamente el codigo TOTP durante el login en un solo paso cuando el usuario ya tiene TOTP habilitado.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void validarCodigoDirectoEnLogin(Usuario usuario, String codigoTotp) {
        if (usuario == null) {
            throw new BadCredentialsException(MSG_CODIGO_TOTP_INVALIDO);
        }
        String secret = descifrarSecretoTotp(usuario.getTotpSecretEnc());
        Optional<Long> pasoValido = verificarPasoTotp(secret, codigoTotp, usuario.getTotpUltimoPaso());
        if (pasoValido.isEmpty()) {
            throw new BadCredentialsException(MSG_CODIGO_TOTP_INVALIDO);
        }
        usuario.setTotpUltimoPaso(pasoValido.get());
        usuarioRepository.save(usuario);
    }

    private MfaChallenge construirChallenge(
            Usuario usuario,
            String challengeHash,
            TipoDesafioMfa tipo,
            ClientMetadata metadata) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        MfaChallenge challenge = new MfaChallenge();
        challenge.setChallengeHash(challengeHash);
        challenge.setUsuario(usuario);
        challenge.setTipo(tipo);
        challenge.setIntentos(0);
        challenge.setConsumido(false);
        challenge.setExpiraEn(now.plusMinutes(Math.max(1L, challengeMinutes)));
        challenge.setIpOrigen(metadata.ipOrigen());
        challenge.setUserAgent(metadata.userAgent());
        return challenge;
    }

    private boolean esDesafioVigente(
            MfaChallenge challenge,
            LocalDateTime now,
            int intentosActuales,
            int limiteIntentos) {
        return !challenge.isConsumido()
                && challenge.getExpiraEn() != null
                && challenge.getExpiraEn().isAfter(now)
                && intentosActuales < limiteIntentos;
    }

    private boolean esOtpCorreoVigente(MfaChallenge challenge, LocalDateTime now) {
        return challenge.getEmailOtpHash() != null
                && !challenge.getEmailOtpHash().isBlank()
                && challenge.getEmailOtpExpiraEn() != null
                && challenge.getEmailOtpExpiraEn().isAfter(now);
    }

    private boolean coincideOtpCorreo(String codigoIngresado, String expectedHash) {
        if (codigoIngresado == null || codigoIngresado.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String candidateHash = sha256Hex(codigoIngresado.trim());
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String generarCodigoOtpSeisDigitos() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private Optional<Long> verificarPasoTotp(String secret, String codigoTotp, Long ultimoPaso) {
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        return totpService.verificarCodigoConPaso(secret, codigoTotp, ultimoPaso);
    }

    private void registrarIntentoFallidoChallenge(
            MfaChallenge challenge,
            int intentosActuales,
            int limiteIntentos) {
        int nuevosIntentos = intentosActuales + 1;
        challenge.setIntentos(nuevosIntentos);
        if (nuevosIntentos >= limiteIntentos) {
            challenge.setConsumido(true);
        }
        mfaChallengeRepository.save(challenge);
    }

    private void aplicarVerificacionExitosa(
            Usuario usuario,
            MfaChallenge challenge,
            long pasoCoincidente,
            LocalDateTime now) {
        usuario.setTotpUltimoPaso(pasoCoincidente);
        if (challenge.getTipo() == TipoDesafioMfa.ENROLLMENT) {
            usuario.setTotpHabilitado(true);
            usuario.setTotpConfiguradoEn(now);
        }
        challenge.setConsumido(true);
        usuarioRepository.save(usuario);
        mfaChallengeRepository.save(challenge);
    }

    private String descifrarSecretoTotp(String totpSecretEnc) {
        if (totpSecretEnc == null || totpSecretEnc.isBlank()) {
            return null;
        }
        if (totpSecretEnc.startsWith(ENC_PREFIX)) {
            return encryptionHelper.decryptToString(totpSecretEnc.substring(ENC_PREFIX.length()));
        }
        try {
            String descifrado = encryptionHelper.decryptToString(totpSecretEnc);
            return (descifrado != null && !descifrado.isBlank()) ? descifrado : totpSecretEnc;
        } catch (RuntimeException ex) {
            return totpSecretEnc;
        }
    }

    private String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", ex);
        }
    }
}
