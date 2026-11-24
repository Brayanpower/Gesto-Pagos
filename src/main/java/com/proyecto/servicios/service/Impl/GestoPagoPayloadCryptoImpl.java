package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.GestoPagoPayloadCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Cifrado dedicado para el contrato externo de mensajeria GestoPago (sendTx.do / verifyTx.do).
 * El almacenamiento interno de secretos en base de datos utiliza AES/GCM/NoPadding mediante AesEncryptionImpl.
 */
@Component
@Slf4j
public class GestoPagoPayloadCryptoImpl implements GestoPagoPayloadCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH_BYTES = 16;

    private final ObjectMapper objectMapper;
    private final String keyBase64;
    private final String ivBase64;

    public GestoPagoPayloadCryptoImpl(
            ObjectMapper objectMapper,
            @Value("${gestopago.crypto.key:${app.crypto.key:}}") String keyBase64,
            @Value("${gestopago.crypto.iv:${app.crypto.iv:}}") String ivBase64) {
        this.objectMapper = objectMapper;
        this.keyBase64 = keyBase64;
        this.ivBase64 = ivBase64;
    }

    @Override
    public String encrypt(Object data) {
        if (data == null) {
            return null;
        }
        String plain = data instanceof String text ? text : toJson(data);
        try {
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE);
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.error("Error criptografico al cifrar payload para GestoPago ({})", ex.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible cifrar la solicitud para GestoPago", ex);
        }
    }

    @Override
    public String decryptToString(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted.trim());
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.debug("No fue posible descifrar el payload de GestoPago ({})", ex.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible descifrar la respuesta de GestoPago", ex);
        }
    }

    @Override
    public <T> T decrypt(String encrypted, Class<T> type) {
        String plain = decryptToString(encrypted);
        if (plain == null) {
            return null;
        }
        if (type == String.class) {
            return type.cast(plain);
        }
        try {
            return objectMapper.readValue(plain, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible deserializar la respuesta descifrada de GestoPago", ex);
        }
    }

    /**
     * Inicializa AES/CBC/PKCS5Padding requerido por el protocolo externo de integracion GestoPago
     * con la llave y el vector de inicializacion asignados al comercio.
     */
    @SuppressWarnings("java:S5542")
    private Cipher createCipher(int cipherMode) throws GeneralSecurityException {
        SecretKey secretKey = buildSecretKey();
        IvParameterSpec ivSpec = buildIvSpec();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(cipherMode, secretKey, ivSpec);
        return cipher;
    }

    private SecretKey buildSecretKey() {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("La llave criptografica de GestoPago no esta configurada");
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64.trim());
        int len = keyBytes.length;
        if (len != 16 && len != 24 && len != 32) {
            throw new IllegalStateException("Longitud invalida para la llave AES de GestoPago");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private IvParameterSpec buildIvSpec() {
        if (ivBase64 == null || ivBase64.isBlank()) {
            throw new IllegalStateException("El vector IV criptografico de GestoPago no esta configurado");
        }
        byte[] ivBytes = Base64.getDecoder().decode(ivBase64.trim());
        if (ivBytes.length != IV_LENGTH_BYTES) {
            throw new IllegalStateException("Longitud invalida para el vector IV de GestoPago (se esperan 16 bytes)");
        }
        return new IvParameterSpec(ivBytes);
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar la solicitud de GestoPago", ex);
        }
    }
}
