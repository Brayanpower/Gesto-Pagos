package com.proyecto.servicios.service.Impl;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public class AesEncryptionImpl {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesEncryptionImpl() {
    }

    public static String aesCipher(String mode, String input, String key) {
        if (input == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("El texto de entrada y la llave criptografica son obligatorios");
        }
        byte[] keyBytes = Base64.getDecoder().decode(key.trim());
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        if (Mode.ENCRYPT.name().equalsIgnoreCase(mode)) {
            return encrypt(input, secretKey);
        } else if (Mode.DECRYPT.name().equalsIgnoreCase(mode)) {
            return decrypt(input, secretKey);
        }
        throw new IllegalArgumentException("Modo criptografico no soportado");
    }

    public static String encrypt(String input, SecretKey secretKey) {
        if (input == null || secretKey == null) {
            throw new IllegalArgumentException("La entrada y la llave secreta son obligatorias para cifrar");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("Error criptografico al cifrar carga util ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible cifrar la informacion", e);
        }
    }

    public static String decrypt(String input, SecretKey secretKey) {
        if (input == null || secretKey == null) {
            throw new IllegalArgumentException("La entrada y la llave secreta son obligatorias para descifrar");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(input.trim());
            if (decoded.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Payload cifrado invalido o incompleto");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.debug("No fue posible descifrar la cadena recibida ({})", e.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible descifrar la informacion", e);
        }
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }
}