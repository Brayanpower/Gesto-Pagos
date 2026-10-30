package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.EncryptionHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EncryptionHelperImpl implements EncryptionHelper {

    private final ObjectMapper objectMapper;
    private final String iv;
    private final String key;

    public EncryptionHelperImpl(
            ObjectMapper objectMapper,
            @Value("${app.crypto.iv}") String iv,
            @Value("${app.crypto.key}") String key) {
        this.objectMapper = objectMapper;
        this.iv = iv;
        this.key = key;
    }

    @Override
    public String encrypt(Object data) {
        if (data == null) {
            return null;
        }
        String plain = data instanceof String ? (String) data : toJson(data);
        String result = AesEncryptionImpl.AESCipher("ENCRYPT", plain, iv, key);
        if (result == null) {
            throw new IllegalStateException("No fue posible cifrar la informacion");
        }
        return result;
    }

    @Override
    public String decryptToString(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        String result = AesEncryptionImpl.AESCipher("DECRYPT", encrypted, iv, key);
        if (result == null) {
            throw new IllegalStateException("No fue posible descifrar la informacion");
        }
        return result;
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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible convertir la informacion descifrada", e);
        }
    }

    @Override
    public <T> T decrypt(String encrypted, TypeReference<T> type) {
        String plain = decryptToString(encrypted);
        if (plain == null) {
            return null;
        }
        try {
            return objectMapper.readValue(plain, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible convertir la informacion descifrada", e);
        }
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar la informacion", e);
        }
    }
}