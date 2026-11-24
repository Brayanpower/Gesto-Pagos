package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Pruebas unitarias de GestoPagoPayloadCryptoImpl (AES/CBC/PKCS5Padding)")
class GestoPagoPayloadCryptoImplTest {

    private static final String VALID_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String VALID_IV = "MDEyMzQ1Njc4OWFiY2RlZg==";

    @Test
    @DisplayName("Cifra y descifra objetos SendTx con AES/CBC/PKCS5Padding")
    void encryptYDecrypt_roundTripSendTx() {
        GestoPagoPayloadCryptoImpl crypto = new GestoPagoPayloadCryptoImpl(new ObjectMapper(), VALID_KEY, VALID_IV);
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decryptToString(null));

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .upc("1234567890")
                .unidad("GPS83-TPV-17")
                .build();

        String signed = crypto.encrypt(tx);
        assertNotNull(signed);

        SendTx recovered = crypto.decrypt(signed, SendTx.class);
        assertEquals(2284, recovered.getIdServicio());
        assertEquals(14302, recovered.getIdProducto());
        assertEquals("1234567890", recovered.getUpc());
    }

    @Test
    @DisplayName("Llave, IV o ciphertext invalidos lanzan IllegalStateException")
    void parametrosInvalidos_lanzanIllegalStateException() {
        ObjectMapper mapper = new ObjectMapper();
        GestoPagoPayloadCryptoImpl sinConfig = new GestoPagoPayloadCryptoImpl(mapper, "", VALID_IV);
        assertThrows(IllegalStateException.class, () -> sinConfig.encrypt("hola"));

        GestoPagoPayloadCryptoImpl ivInvalido = new GestoPagoPayloadCryptoImpl(mapper, VALID_KEY, "AQID");
        assertThrows(IllegalStateException.class, () -> ivInvalido.encrypt("hola"));

        GestoPagoPayloadCryptoImpl keyInvalida = new GestoPagoPayloadCryptoImpl(mapper, "AQID", VALID_IV);
        assertThrows(IllegalStateException.class, () -> keyInvalida.encrypt("hola"));

        GestoPagoPayloadCryptoImpl valido = new GestoPagoPayloadCryptoImpl(mapper, VALID_KEY, VALID_IV);
        assertThrows(IllegalStateException.class, () -> valido.decryptToString("no-es-base64-valido!"));
    }
}
