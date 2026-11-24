package com.proyecto.servicios.service.Impl;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        if (Mode.ENCRYPT.name().equalsIgnoreCase(mode)) {
            return encrypt(input, secretKey);
        } else if (Mode.DECRYPT.name().equalsIgnoreCase(mode)) {
            return decrypt(input, secretKey);
        }
        return null;
    }

    public static String encrypt(String input, SecretKey secretKey) {
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
        } catch (Exception e) {
            log.error("Error criptografico al cifrar carga util ({})", e.getClass().getSimpleName());
        }
        return null;
    }

    public static String decrypt(String input, SecretKey secretKey) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(input));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("No fue posible descifrar la cadena recibida ({})", e.getClass().getSimpleName());
        }
        return null;
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }
}