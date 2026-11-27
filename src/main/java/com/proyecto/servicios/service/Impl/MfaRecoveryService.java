package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.MfaRecoveryCode;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.repositorys.seguridad.MfaRecoveryCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Administra códigos de recuperación de un solo uso para restablecer 2FA TOTP ante pérdida del autenticador.
 * Los códigos se persisten únicamente como hashes SHA-256 hexadecimales.
 */
@Service
public class MfaRecoveryService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOTAL_CODES = 8;
    private static final int SEGMENT_LENGTH = 4;
    private static final int RAW_CODE_LENGTH = SEGMENT_LENGTH * 2;
    private static final String SHA_256 = "SHA-256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MfaRecoveryCodeRepository recoveryCodeRepository;

    public MfaRecoveryService(MfaRecoveryCodeRepository recoveryCodeRepository) {
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    /**
     * Invalida cualquier código previo del usuario, genera 8 códigos nuevos en formato {@code XXXX-XXXX},
     * almacena sus hashes SHA-256 y devuelve los códigos en claro una sola vez.
     */
    @Transactional
    public List<String> generarCodigosRecuperacion(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException("El usuario es obligatorio para generar codigos de recuperacion");
        }
        recoveryCodeRepository.deleteByUsuarioId(usuario.getId());

        Set<String> codigosUnicos = new HashSet<>();
        List<String> codigosPlanos = new ArrayList<>(TOTAL_CODES);
        List<MfaRecoveryCode> entidades = new ArrayList<>(TOTAL_CODES);

        while (codigosPlanos.size() < TOTAL_CODES) {
            String codigo = generarCodigoAleatorio();
            if (codigosUnicos.add(codigo)) {
                codigosPlanos.add(codigo);
                MfaRecoveryCode entidad = new MfaRecoveryCode();
                entidad.setUsuario(usuario);
                entidad.setCodeHash(sha256Hex(codigo));
                entidad.setUsado(false);
                entidades.add(entidad);
            }
        }

        recoveryCodeRepository.saveAll(entidades);
        return codigosPlanos;
    }

    /**
     * Valida y consume de forma atómica un código de recuperación no usado.
     */
    @Transactional
    public boolean consumirCodigoRecuperacion(Usuario usuario, String codigoRaw) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }
        String normalizado = normalizarCodigo(codigoRaw);
        if (normalizado == null) {
            return false;
        }
        String hash = sha256Hex(normalizado);
        Optional<MfaRecoveryCode> codeOpt =
                recoveryCodeRepository.findByUsuarioIdAndCodeHashAndUsadoFalse(usuario.getId(), hash);
        if (codeOpt.isEmpty()) {
            return false;
        }
        MfaRecoveryCode code = codeOpt.get();
        code.setUsado(true);
        code.setFechaUso(LocalDateTime.now(ZoneId.systemDefault()));
        recoveryCodeRepository.save(code);
        return true;
    }

    @Transactional
    public void invalidarCodigos(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        recoveryCodeRepository.deleteByUsuarioId(usuarioId);
    }

    private String generarCodigoAleatorio() {
        StringBuilder sb = new StringBuilder(RAW_CODE_LENGTH + 1);
        for (int i = 0; i < RAW_CODE_LENGTH; i++) {
            if (i == SEGMENT_LENGTH) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalizarCodigo(String codigoRaw) {
        if (codigoRaw == null || codigoRaw.isBlank()) {
            return null;
        }
        String limpio = codigoRaw.trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replace(" ", "");
        if (limpio.length() != RAW_CODE_LENGTH) {
            return null;
        }
        return limpio.substring(0, SEGMENT_LENGTH) + "-" + limpio.substring(SEGMENT_LENGTH);
    }

    private String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", ex);
        }
    }
}
