package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Pruebas unitarias de AesEncryptionImpl (AES/GCM) y EncryptionHelperImpl")
class AesEncryptionImplTest {

    private static final String VALID_KEY = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";

    @Test
    @DisplayName("Cifra y descifra con AES/GCM generando IV unico por operacion")
    void aesCipher_roundTripConIvUnico() {
        String cipher1 = AesEncryptionImpl.aesCipher("ENCRYPT", "secreto-totp", VALID_KEY);
        String cipher2 = AesEncryptionImpl.aesCipher("ENCRYPT", "secreto-totp", VALID_KEY);

        assertNotNull(cipher1);
        assertNotNull(cipher2);
        assertNotEquals(cipher1, cipher2);
        assertEquals("secreto-totp", AesEncryptionImpl.aesCipher("DECRYPT", cipher1, VALID_KEY));
        assertEquals("secreto-totp", AesEncryptionImpl.aesCipher("DECRYPT", cipher2, VALID_KEY));
    }

    @Test
    @DisplayName("Rechaza payload manipulado, modo invalido o parametros nulos con excepcion explicita")
    void aesCipher_erroresCriptograficos_lanzanExcepcion() {
        String cipher = AesEncryptionImpl.aesCipher("ENCRYPT", "token-sensible", VALID_KEY);
        String alterado = "A" + cipher.substring(1);

        assertThrows(IllegalStateException.class,
                () -> AesEncryptionImpl.aesCipher("DECRYPT", alterado, VALID_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptionImpl.aesCipher("UNKNOWN", "abc", VALID_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptionImpl.aesCipher("ENCRYPT", null, VALID_KEY));
    }

    @Test
    @DisplayName("EncryptionHelperImpl soporta objetos JSON y cadenas de forma segura")
    void encryptionHelper_roundTripJsonYTexto() {
        EncryptionHelperImpl helper = new EncryptionHelperImpl(new ObjectMapper(), VALID_KEY);

        assertNull(helper.encrypt(null));
        assertNull(helper.decryptToString(null));

        String cifrado = helper.encrypt(Map.of("usuario", "admin", "nivel", 1));
        @SuppressWarnings("unchecked")
        Map<String, Object> descifrado = helper.decrypt(cifrado, Map.class);
        assertEquals("admin", descifrado.get("usuario"));
        assertEquals(1, descifrado.get("nivel"));
    }
}
