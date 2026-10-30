package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.EncryptionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de GestoPagoTokenServiceImpl (Seguridad Bearer Token)")
class GestoPagoTokenServiceImplTest {

    private static final String TEST_IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final String TEST_KEY = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";

    @Mock private GestoPagoAuthClient authClient;
    @Mock private GestoPagoTokenRepository tokenRepository;
    @Mock private GestoPagoTokenMapper tokenMapper;

    private GestoPagoTokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        EncryptionHelper encryptionHelper = new EncryptionHelperImpl(new ObjectMapper(), TEST_IV, TEST_KEY);
        tokenService = new GestoPagoTokenServiceImpl(authClient, tokenRepository, tokenMapper, encryptionHelper);
        ReflectionTestUtils.setField(tokenService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(tokenService, "codigoDispositivo", "GPS83-TPV-17");
        ReflectionTestUtils.setField(tokenService, "password", "secretPass");
        ReflectionTestUtils.setField(tokenService, "refreshRateMs", 3600000L);
    }

    @Test
    @DisplayName("Usa token Bearer provisto por variable de entorno sin consultar red ni BD")
    void obtenerBearerTokenValido_conVariableDeEntorno_retornaTokenDirecto() {
        ReflectionTestUtils.setField(tokenService, "staticBearerToken", "Bearer token-seguro-env-123");

        String token = tokenService.obtenerBearerTokenValido();

        assertEquals("token-seguro-env-123", token);
        verify(authClient, never()).authenticate(any(), any(), any());
        verify(tokenRepository, never()).findByIdDistribuidorAndCodigoDispositivo(any(), any());
    }

    @Test
    @DisplayName("Cifra el token Bearer con AES antes de persistirlo en base de datos y lo descifra al leerlo")
    void renovarYObtenerToken_cifraEnReposo() {
        GestoPagoAuthResponse authResp = new GestoPagoAuthResponse();
        authResp.setToken("jwt-gestopago-real");
        authResp.setExpiresIn(3600L);

        when(authClient.authenticate(83, "GPS83-TPV-17", "secretPass")).thenReturn(authResp);
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.empty());
        GestoPagoToken mapped = new GestoPagoToken();
        mapped.setExpiresIn(3600L);
        mapped.setFechaActualizacion(LocalDateTime.now());
        when(tokenMapper.toEntity(authResp)).thenReturn(mapped);

        tokenService.renovarToken();

        ArgumentCaptor<GestoPagoToken> captor = ArgumentCaptor.forClass(GestoPagoToken.class);
        verify(tokenRepository).save(captor.capture());

        GestoPagoToken guardado = captor.getValue();
        assertTrue(guardado.getToken().startsWith("ENC:"));
        assertNotEquals("jwt-gestopago-real", guardado.getToken());

        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.of(guardado));

        assertEquals("jwt-gestopago-real", tokenService.obtenerBearerTokenValido());
    }

    @Test
    @DisplayName("Lanza ProveedorExternoException si no existe token activo y falla la renovacion")
    void obtenerBearerTokenValido_fallaRenovacion_lanzaExcepcion() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.empty());
        when(authClient.authenticate(83, "GPS83-TPV-17", "secretPass"))
                .thenThrow(new RuntimeException("Auth service down"));

        assertThrows(ProveedorExternoException.class, () -> tokenService.obtenerBearerTokenValido());
    }
}

