package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.RefreshToken;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.RefreshTokenRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long DEFAULT_REFRESH_EXPIRATION_MS = 604_800_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final String SHA_256 = "SHA-256";
    private static final String MSG_REFRESH_INVALIDO = "Refresh token invalido o expirado";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UsuarioRepository usuarioRepository,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.refreshExpirationMs = refreshExpirationMs > 0L ? refreshExpirationMs : DEFAULT_REFRESH_EXPIRATION_MS;
    }

    public record RotationResult(Usuario usuario, String nuevoRefreshToken) {
    }

    @Transactional
    public String emitirRefreshToken(Usuario usuario, ClientMetadata metadata) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no puede ser nulo al emitir refresh token");
        }
        String rawToken = generarTokenOpaco();
        String tokenHash = sha256Hex(rawToken);
        String familiaId = UUID.randomUUID().toString();
        persistirNuevoToken(usuario, familiaId, tokenHash, metadata);
        return rawToken;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public RotationResult rotarRefreshToken(String rawRefreshToken, ClientMetadata metadata) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException(MSG_REFRESH_INVALIDO);
        }

        String tokenHash = sha256Hex(rawRefreshToken.trim());
        RefreshToken actual = refreshTokenRepository.findWithUsuarioByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException(MSG_REFRESH_INVALIDO));

        if (actual.isRevocado()) {
            manejarReusoDetectado(actual);
            throw new BadCredentialsException(MSG_REFRESH_INVALIDO);
        }

        LocalDateTime ahora = LocalDateTime.now(ZoneId.systemDefault());
        if (actual.getExpiraEn() == null || !actual.getExpiraEn().isAfter(ahora)) {
            revocarTokenYFamilia(actual);
            throw new BadCredentialsException(MSG_REFRESH_INVALIDO);
        }

        Usuario usuario = actual.getUsuario();
        if (!esUsuarioYClienteActivos(usuario)) {
            revocarTokenYFamilia(actual);
            throw new DisabledException("El usuario o cliente se encuentra inactivo en el sistema");
        }

        String nuevoRawToken = generarTokenOpaco();
        String nuevoHash = sha256Hex(nuevoRawToken);
        actual.setRevocado(true);
        actual.setReemplazadoPor(nuevoHash);
        refreshTokenRepository.save(actual);

        persistirNuevoToken(usuario, actual.getFamiliaId(), nuevoHash, metadata);
        return new RotationResult(usuario, nuevoRawToken);
    }

    @Transactional
    public void revocarToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = sha256Hex(rawRefreshToken.trim());
        refreshTokenRepository.findWithUsuarioByTokenHash(tokenHash)
                .ifPresent(token -> {
                    if (!token.isRevocado()) {
                        token.setRevocado(true);
                        refreshTokenRepository.save(token);
                    }
                });
    }

    @Transactional
    public void revocarTodasLasSesiones(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        refreshTokenRepository.revocarTodosPorUsuarioId(usuarioId);
    }

    public String sha256Hex(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El valor para calcular SHA-256 no puede ser nulo");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", ex);
        }
    }

    private void manejarReusoDetectado(RefreshToken actual) {
        Usuario usuario = actual.getUsuario();
        if (usuario != null) {
            usuario.incrementarTokenVersion();
            usuarioRepository.save(usuario);
        }
        refreshTokenRepository.revocarPorFamiliaId(actual.getFamiliaId());
        log.warn("Reuso detectado de refresh token para familiaId={}", actual.getFamiliaId());
    }

    private void revocarTokenYFamilia(RefreshToken actual) {
        actual.setRevocado(true);
        refreshTokenRepository.save(actual);
        refreshTokenRepository.revocarPorFamiliaId(actual.getFamiliaId());
    }

    private boolean esUsuarioYClienteActivos(Usuario usuario) {
        if (usuario == null || !usuario.isActivo()) {
            return false;
        }
        return usuario.getCliente() == null || usuario.getCliente().isActivo();
    }

    private void persistirNuevoToken(
            Usuario usuario,
            String familiaId,
            String tokenHash,
            ClientMetadata metadata) {
        ClientMetadata meta = metadata != null ? metadata : ClientMetadata.desconocido();
        long expirationSeconds = Math.max(1L, refreshExpirationMs / MILLIS_PER_SECOND);

        RefreshToken nuevo = new RefreshToken();
        nuevo.setUsuario(usuario);
        nuevo.setFamiliaId(familiaId);
        nuevo.setTokenHash(tokenHash);
        nuevo.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(expirationSeconds));
        nuevo.setRevocado(false);
        nuevo.setIpOrigen(meta.ipOrigen());
        nuevo.setUserAgent(meta.userAgent());
        refreshTokenRepository.save(nuevo);
    }

    private String generarTokenOpaco() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
