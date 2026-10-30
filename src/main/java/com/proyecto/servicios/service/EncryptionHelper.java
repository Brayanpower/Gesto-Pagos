package com.proyecto.servicios.service;

import com.fasterxml.jackson.core.type.TypeReference;

public interface EncryptionHelper {

    String encrypt(Object data);

    String decryptToString(String encrypted);

    <T> T decrypt(String encrypted, Class<T> type);

    <T> T decrypt(String encrypted, TypeReference<T> type);
}