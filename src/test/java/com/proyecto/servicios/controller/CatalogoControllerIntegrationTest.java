package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.RateLimitExceededException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.CatalogoCategoriaResponse;
import com.proyecto.servicios.security.JwtAccessDeniedHandler;
import com.proyecto.servicios.security.JwtAuthenticationEntryPoint;
import com.proyecto.servicios.security.JwtAuthenticationFilter;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.security.PublicEndpointRateLimiter;
import com.proyecto.servicios.security.SecurityConfig;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.GestoPagoImagenService;
import com.proyecto.servicios.service.GestoPagoImagenService.ImagenRecurso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogoController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        JwtAuthenticationFilter.class
})
@DisplayName("Pruebas Web y Seguridad de CatalogoController (Catálogo, Logos y Referencias GestoPago)")
class CatalogoControllerIntegrationTest {

    private static final byte[] PNG_DEMO = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPG_DEMO = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Autowired private MockMvc mockMvc;

    @MockBean private CatalogoService catalogoService;
    @MockBean private GestoPagoImagenService gestoPagoImagenService;
    @MockBean private PublicEndpointRateLimiter publicEndpointRateLimiter;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /gestopago/catalogo sigue siendo publico y retorna los servicios")
    void obtenerCatalogo_publico_retorna200() throws Exception {
        CatalogoProducto prod = new CatalogoProducto();
        prod.setIdServicio("79");
        prod.setServicio("CFE");
        prod.setIdProducto("101");
        prod.setNombreProducto("Pago CFE");
        when(catalogoService.obtenerCatalogo()).thenReturn(List.of(prod));

        mockMvc.perform(get("/gestopago/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idServicio").value("79"))
                .andExpect(jsonPath("$[0].servicio").value("CFE"));
    }

    @Test
    @DisplayName("GET /gestopago/servicios/{idServicio}/logo es publico, retorna PNG y Cache-Control 24h")
    void obtenerLogoServicio_publico_retornaPngConCache() throws Exception {
        when(gestoPagoImagenService.obtenerLogoServicio("79"))
                .thenReturn(Optional.of(new ImagenRecurso(PNG_DEMO, MediaType.IMAGE_PNG)));

        mockMvc.perform(get("/gestopago/servicios/79/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(content().bytes(PNG_DEMO));
    }

    @Test
    @DisplayName("GET /gestopago/servicios/{idServicio}/referencia retorna JPEG si existe y 404 si no existe")
    void obtenerImagenReferencia_retornaJpegO404() throws Exception {
        when(gestoPagoImagenService.obtenerImagenReferencia("79"))
                .thenReturn(Optional.of(new ImagenRecurso(JPG_DEMO, MediaType.IMAGE_JPEG)));
        when(gestoPagoImagenService.obtenerImagenReferencia("999"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/gestopago/servicios/79/referencia"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPG_DEMO));

        mockMvc.perform(get("/gestopago/servicios/999/referencia"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /gestopago/servicios/{idServicio}/logo con id invalido retorna 400 Bad Request")
    void obtenerLogoServicio_idInvalido_retorna400() throws Exception {
        when(gestoPagoImagenService.obtenerLogoServicio("abc"))
                .thenThrow(new ReglaNegocioException("El idServicio debe ser un numero entero positivo."));

        mockMvc.perform(get("/gestopago/servicios/abc/logo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    @DisplayName("GET /gestopago/servicios/{idServicio}/logo retorna 429 con Retry-After al superar rate limit")
    void obtenerLogoServicio_rateLimitExcedido_retorna429() throws Exception {
        doThrow(new RateLimitExceededException("Demasiadas solicitudes de imagenes", 30L))
                .when(publicEndpointRateLimiter).verificarLimiteImagenes(anyString());

        mockMvc.perform(get("/gestopago/servicios/79/logo"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.codigo").value(429));
    }

    @Test
    @DisplayName("GET /gestopago/catalogo con paginacion, /categorias y /{idServicio}/{idProducto} son publicos")
    void catalogoEndpointsNuevos_publicos_retornan200() throws Exception {
        CatalogoProducto prod = new CatalogoProducto();
        prod.setIdServicio("79");
        prod.setIdProducto("101");
        prod.setServicio("CFE");
        prod.setNombreProducto("Pago CFE");

        when(catalogoService.buscarCatalogoPaginado("2", null, "CFE", 0, 5))
                .thenReturn(PageResponse.<CatalogoProducto>builder()
                        .contenido(List.of(prod))
                        .paginaActual(0)
                        .tamanoPagina(5)
                        .totalElementos(1L)
                        .totalPaginas(1)
                        .primera(true)
                        .ultima(true)
                        .build());
        when(catalogoService.obtenerCategorias()).thenReturn(List.of(
                CatalogoCategoriaResponse.builder()
                        .idCatTipoServicio("2")
                        .totalProductos(1L)
                        .servicios(List.of("CFE"))
                        .build()));
        when(catalogoService.obtenerDetalleProducto("79", "101")).thenReturn(prod);

        mockMvc.perform(get("/gestopago/catalogo")
                        .param("categoria", "2")
                        .param("q", "CFE")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idProducto").value("101"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mockMvc.perform(get("/gestopago/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCatTipoServicio").value("2"))
                .andExpect(jsonPath("$[0].servicios[0]").value("CFE"));

        mockMvc.perform(get("/gestopago/catalogo/79/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreProducto").value("Pago CFE"));
    }
}
