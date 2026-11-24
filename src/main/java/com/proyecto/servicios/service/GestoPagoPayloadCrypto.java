package com.proyecto.servicios.service;

public interface GestoPagoPayloadCrypto {

    String encrypt(Object data);

    String decryptToString(String encrypted);

    <T> T decrypt(String encrypted, Class<T> type);
}
