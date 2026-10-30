package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoSendTx;
import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult.EstadoProveedor;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;
import com.proyecto.servicios.security.LogSanitizer;
import com.proyecto.servicios.service.EncryptionHelper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de GestoPagoSendTxServiceImpl")
class GestoPagoSendTxServiceImplTest {

    private static final String TEST_IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final String TEST_KEY = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";

    @Mock
    private GestoPagoSendTx gestoPagoSendTx;

    private EncryptionHelper encryptionHelper;
    private GestoPagoSendTxServiceImpl servicio;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        encryptionHelper = new EncryptionHelperImpl(objectMapper, TEST_IV, TEST_KEY);
        servicio = new GestoPagoSendTxServiceImpl(gestoPagoSendTx, encryptionHelper, objectMapper);
    }

    @Test
    @DisplayName("Envia payload cifrado en signed + random y parsea XML exitoso CODIGO 01")
    void ejecutarTransaccion_xmlExitoso_devuelveAprobada() {
        String xmlExito = "<?xml version='1.0' encoding='UTF-8'?>"
                + "<RESPONSE>"
                + "<MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE>"
                + "<ID_TX>99887766</ID_TX>"
                + "<NUM_AUTORIZACION>AUT123456</NUM_AUTORIZACION>"
                + "</RESPONSE>";

        when(gestoPagoSendTx.sendTx(any())).thenReturn(ResponseEntity.ok(xmlExito));

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .upc("1234567890")
                .horaLocal("20261030093000")
                .build();

        GestoPagoTxResult resultado = servicio.ejecutarTransaccion(tx);

        assertEquals(EstadoProveedor.APROBADA, resultado.getEstado());
        assertEquals("01", resultado.getCodigo());
        assertEquals("99887766", resultado.getIdTx());
        assertEquals("AUT123456", resultado.getNumAutorizacion());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> formCaptor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(gestoPagoSendTx).sendTx(formCaptor.capture());

        MultiValueMap<String, String> form = formCaptor.getValue();
        assertNotNull(form.getFirst("random"));
        String signed = form.getFirst("signed");
        assertNotNull(signed);

        SendTx descifrado = encryptionHelper.decrypt(signed, SendTx.class);
        assertEquals(2284, descifrado.getIdServicio());
        assertEquals(14302, descifrado.getIdProducto());
        assertEquals(0, new BigDecimal("100.00").compareTo(descifrado.getMontoPago()));
        assertEquals("1234567890", descifrado.getUpc());
    }

    @Test
    @DisplayName("Soporta respuesta cifrada en AES Base64 devuelta por GestoPago")
    void ejecutarTransaccion_respuestaCifradaAes_descifraYAprueba() {
        String jsonClaro = "{\"MENSAJE\":{\"CODIGO\":\"01\",\"TEXTO\":\"Recarga aplicada\"},\"ID_TX\":\"554433\",\"NUM_AUTORIZACION\":\"OK777\"}";
        String cuerpoCifrado = encryptionHelper.encrypt(jsonClaro);

        when(gestoPagoSendTx.sendTx(any())).thenReturn(ResponseEntity.ok(cuerpoCifrado));

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .build();

        GestoPagoTxResult resultado = servicio.ejecutarTransaccion(tx);

        assertEquals(EstadoProveedor.APROBADA, resultado.getEstado());
        assertEquals("554433", resultado.getIdTx());
        assertEquals("OK777", resultado.getNumAutorizacion());
    }

    @Test
    @DisplayName("Codigo de rechazo de negocio del proveedor devuelve RECHAZADA_PROVEEDOR")
    void ejecutarTransaccion_rechazoNegocio_devuelveRechazada() {
        String xmlError = "<?xml version='1.0' encoding='UTF-8'?>"
                + "<RESPONSE><MENSAJE><CODIGO>05</CODIGO><TEXTO>Numero destino invalido</TEXTO></MENSAJE></RESPONSE>";

        when(gestoPagoSendTx.sendTx(any())).thenReturn(ResponseEntity.ok(xmlError));

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .build();

        GestoPagoTxResult resultado = servicio.ejecutarTransaccion(tx);

        assertEquals(EstadoProveedor.RECHAZADA_PROVEEDOR, resultado.getEstado());
        assertEquals("05", resultado.getCodigo());
    }

    @Test
    @DisplayName("Timeout de lectura NO asume fallo ni lanza error de reverso: devuelve PENDIENTE_PROVEEDOR")
    void ejecutarTransaccion_readTimeout_devuelvePendienteConciliacion() {
        Request dummyReq = Request.create(
                Request.HttpMethod.POST,
                "https://gestopago.portalventas.net/sistema/service/sendTx.do",
                Collections.emptyMap(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate()
        );
        RetryableException timeoutEx = new RetryableException(
                -1,
                "Read timed out executing POST",
                Request.HttpMethod.POST,
                new SocketTimeoutException("Read timed out"),
                (Long) null,
                dummyReq
        );

        when(gestoPagoSendTx.sendTx(any())).thenThrow(timeoutEx);

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .upc("7777777777")
                .build();

        GestoPagoTxResult resultado = servicio.ejecutarTransaccion(tx);

        assertEquals(EstadoProveedor.PENDIENTE_PROVEEDOR, resultado.getEstado());
    }

    @Test
    @DisplayName("ConnectException previa al envio lanza ProveedorExternoException para reversar sin riesgo")
    void ejecutarTransaccion_connectException_lanzaProveedorExternoException() {
        Request dummyReq = Request.create(
                Request.HttpMethod.POST,
                "https://gestopago.portalventas.net/sistema/service/sendTx.do",
                Collections.emptyMap(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate()
        );
        FeignException connectFail = new RetryableException(
                -1,
                "Connection refused",
                Request.HttpMethod.POST,
                new ConnectException("Connection refused"),
                (Long) null,
                dummyReq
        );

        when(gestoPagoSendTx.sendTx(any())).thenThrow(connectFail);

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .build();

        assertThrows(ProveedorExternoException.class, () -> servicio.ejecutarTransaccion(tx));
    }

    @Test
    @DisplayName("HTTP 401 del proveedor lanza ProveedorExternoException sin exponer token")
    void ejecutarTransaccion_http401_lanzaProveedorExternoException() {
        when(gestoPagoSendTx.sendTx(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized"));

        SendTx tx = SendTx.builder()
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .unidad("GPS83-TPV-17")
                .build();

        assertThrows(ProveedorExternoException.class, () -> servicio.ejecutarTransaccion(tx));
    }

    @Test
    @DisplayName("LogSanitizer oculta tokens Bearer y passwords en mensajes de error")
    void logSanitizer_ocultaSecretos() {
        String sucio = "Error en POST ?idDistribuidor=83&password=MiSecreto123 con header Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def";
        String limpio = LogSanitizer.sanitize(sucio);

        assertFalse(limpio.contains("MiSecreto123"));
        assertFalse(limpio.contains("eyJhbGciOiJIUzI1NiJ9"));
    }
}

