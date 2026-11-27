package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.service.GestoPagoImagenService.ImagenRecurso;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de GestoPagoImagenServiceImpl")
class GestoPagoImagenServiceImplTest {

    private static final String BASE_URL = "https://gestopago.portalventas.net";
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Mock
    private GestoPagoTokenService tokenService;

    private MockRestServiceServer server;
    private GestoPagoImagenServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GestoPagoImagenServiceImpl(builder.build(), tokenService, "test-api-key");
    }

    @Test
    @DisplayName("obtenerLogoServicio envia Bearer y X-API-Key, devuelve PNG y cachea en memoria")
    void obtenerLogoServicio_exitoYCache() {
        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-123");

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/79.png"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gp-jwt-123"))
                .andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess(PNG_BYTES, MediaType.IMAGE_PNG));

        Optional<ImagenRecurso> primera = service.obtenerLogoServicio("79");
        Optional<ImagenRecurso> segunda = service.obtenerLogoServicio(" 79 ");

        server.verify();
        verify(tokenService, times(1)).obtenerBearerTokenValido();
        assertTrue(primera.isPresent());
        assertEquals(MediaType.IMAGE_PNG, primera.get().mediaType());
        assertArrayEquals(PNG_BYTES, primera.get().contenido());
        assertTrue(segunda.isPresent());
        assertArrayEquals(PNG_BYTES, segunda.get().contenido());
    }

    @Test
    @DisplayName("obtenerImagenReferencia omite X-API-Key cuando es opcional/vacio y devuelve JPEG")
    void obtenerImagenReferencia_sinApiKey_exito() {
        RestClient.Builder builderSinKey = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer serverSinKey = MockRestServiceServer.bindTo(builderSinKey).build();
        GestoPagoImagenServiceImpl serviceSinKey = new GestoPagoImagenServiceImpl(
                builderSinKey.build(), tokenService, "");

        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-abc");

        serverSinKey.expect(requestTo(BASE_URL + "/sistema/images/gestopago/referencias/79.jpg"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gp-jwt-abc"))
                .andExpect(headerDoesNotExist("X-API-Key"))
                .andRespond(withSuccess(JPG_BYTES, MediaType.IMAGE_JPEG));

        Optional<ImagenRecurso> ref = serviceSinKey.obtenerImagenReferencia("79");

        serverSinKey.verify();
        assertTrue(ref.isPresent());
        assertEquals(MediaType.IMAGE_JPEG, ref.get().mediaType());
        assertArrayEquals(JPG_BYTES, ref.get().contenido());
    }

    @Test
    @DisplayName("Ante 401 de GestoPago renueva el token y reintenta una vez")
    void obtenerLogoServicio_401RenuevaTokenYReintenta() {
        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-expirado", "gp-jwt-nuevo");

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/2284.png"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gp-jwt-expirado"))
                .andRespond(withUnauthorizedRequest());

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/2284.png"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gp-jwt-nuevo"))
                .andRespond(withSuccess(PNG_BYTES, MediaType.IMAGE_PNG));

        Optional<ImagenRecurso> result = service.obtenerLogoServicio("2284");

        server.verify();
        verify(tokenService, times(1)).renovarToken();
        assertTrue(result.isPresent());
        assertArrayEquals(PNG_BYTES, result.get().contenido());
    }

    @Test
    @DisplayName("Devuelve Optional.empty ante 404, contenido no-imagen o token no disponible")
    void obtenerImagenes_404ONoImagen_retornaEmpty() {
        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-123");

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/referencias/999.jpg"))
                .andRespond(withResourceNotFound());

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/888.png"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>error</html>".getBytes(StandardCharsets.UTF_8)));

        assertTrue(service.obtenerImagenReferencia("999").isEmpty());
        assertTrue(service.obtenerLogoServicio("888").isEmpty());

        when(tokenService.obtenerBearerTokenValido()).thenThrow(new ProveedorExternoException("sin credenciales"));
        assertTrue(service.obtenerLogoServicio("777").isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Rechaza idServicio invalido o traversal con ReglaNegocioException")
    void obtenerLogoServicio_idInvalido_lanzaReglaNegocioException() {
        assertThrows(ReglaNegocioException.class, () -> service.obtenerLogoServicio(null));
        assertThrows(ReglaNegocioException.class, () -> service.obtenerLogoServicio("0"));
        assertThrows(ReglaNegocioException.class, () -> service.obtenerLogoServicio("-5"));
        assertThrows(ReglaNegocioException.class, () -> service.obtenerLogoServicio("abc"));
        assertThrows(ReglaNegocioException.class, () -> service.obtenerImagenReferencia("../79"));
    }

    @Test
    @DisplayName("Errores transitorios 5xx NO se cachean como miss negativo; 404 si se cachea")
    void obtenerImagenes_errorTransitorioNoSeCacheaY404SiSeCachea() {
        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-123");

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/500.png"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/500.png"))
                .andRespond(withSuccess(PNG_BYTES, MediaType.IMAGE_PNG));
        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/referencias/404.jpg"))
                .andRespond(withResourceNotFound());

        assertTrue(service.obtenerLogoServicio("500").isEmpty());
        Optional<ImagenRecurso> recuperado = service.obtenerLogoServicio("500");
        assertTrue(recuperado.isPresent());
        assertArrayEquals(PNG_BYTES, recuperado.get().contenido());

        assertTrue(service.obtenerImagenReferencia("404").isEmpty());
        assertTrue(service.obtenerImagenReferencia("404").isEmpty());

        server.verify();
    }

    @Test
    @DisplayName("Rechaza imagenes que exceden 5 MB tanto por Content-Length como en lectura streaming")
    void obtenerLogoServicio_excedeTamanoMaximo_retornaEmpty() {
        when(tokenService.obtenerBearerTokenValido()).thenReturn("gp-jwt-123");

        HttpHeaders headersConLengthExcedido = new HttpHeaders();
        headersConLengthExcedido.setContentLength(5L * 1024L * 1024L + 1L);

        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/601.png"))
                .andRespond(withStatus(HttpStatus.OK)
                        .headers(headersConLengthExcedido)
                        .contentType(MediaType.IMAGE_PNG)
                        .body(PNG_BYTES));

        byte[] payloadExcedido = new byte[5 * 1024 * 1024 + 64];
        server.expect(requestTo(BASE_URL + "/sistema/images/gestopago/servicios/602.png"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.IMAGE_PNG)
                        .body(payloadExcedido));

        assertTrue(service.obtenerLogoServicio("601").isEmpty());
        assertTrue(service.obtenerLogoServicio("602").isEmpty());

        server.verify();
    }
}

