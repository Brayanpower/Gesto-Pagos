package com.proyecto.servicios.service.Impl;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class AesEncryptionImpl {

    private AesEncryptionImpl() {
    }

    public static String AESCipher(String mode, String input, String IV, String KEY) {
        byte[] iv = Base64.getDecoder().decode(IV);
        byte[] key = Base64.getDecoder().decode(KEY);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        if ("ENCRYPT".equals(mode)) {
            return encrypt(input, secretKey, ivspec);
        } else if ("DECRYPT".equals(mode)) {
            return decrypt(input, secretKey, ivspec);
        }
        return null;
    }

    public static String encrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Error criptografico al cifrar carga util ({})", e.getClass().getSimpleName());
        }
        return null;
    }

    public static String decrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("No fue posible descifrar la cadena recibida ({})", e.getClass().getSimpleName());
        }
        return null;
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }
}
