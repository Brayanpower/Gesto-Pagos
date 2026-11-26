package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.gestopago.CatalogoCategoriaResponse;
import com.proyecto.servicios.security.ClientIpResolver;
import com.proyecto.servicios.security.PublicEndpointRateLimiter;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.GestoPagoImagenService;
import com.proyecto.servicios.service.GestoPagoImagenService.ImagenRecurso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@Tag(name = "Catálogo GestoPago", description = "Consulta pública del catálogo de productos, logos e imágenes de referencia de GestoPago")
public class CatalogoController {

    private static final long CACHE_IMAGEN_HORAS = 24L;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CatalogoService catalogoService;
    private final GestoPagoImagenService gestoPagoImagenService;
    private final PublicEndpointRateLimiter publicEndpointRateLimiter;

    @Value("${app.auth.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders = false;

    public CatalogoController(
            CatalogoService catalogoService,
            GestoPagoImagenService gestoPagoImagenService,
            PublicEndpointRateLimiter publicEndpointRateLimiter) {
        this.catalogoService = catalogoService;
        this.gestoPagoImagenService = gestoPagoImagenService;
        this.publicEndpointRateLimiter = publicEndpointRateLimiter;
    }

    @GetMapping(value = "/gestopago/catalogo", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener catálogo de productos GestoPago", description = "Permite consultar el catálogo completo o aplicar filtros opcionales por categoría, servicio, búsqueda de texto y paginación")
    public ResponseEntity<Object> obtenerCatalogo(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "servicio", required = false) String servicio,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        boolean tienePaginacion = page != null || size != null;
        boolean tieneFiltros = tieneTexto(categoria) || tieneTexto(servicio) || tieneTexto(query);

        if (tienePaginacion) {
            int pagina = page != null ? page : 0;
            int tamano = size != null ? size : DEFAULT_PAGE_SIZE;
            return ResponseEntity.ok(catalogoService.buscarCatalogoPaginado(categoria, servicio, query, pagina, tamano));
        }

        if (tieneFiltros) {
            return ResponseEntity.ok(catalogoService.buscarCatalogo(categoria, servicio, query));
        }

        List<CatalogoProducto> catalogo = catalogoService.obtenerCatalogo();
        if (catalogo.isEmpty()) {
            GenericResponse respuesta = new GenericResponse();
            respuesta.setCodigo(1);
            respuesta.setMensaje("El catalogo de GestoPago no ha sido cargado aun en MongoDB");
            return new ResponseEntity<>(respuesta, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(catalogo, HttpStatus.OK);
    }

    @GetMapping(value = "/gestopago/catalogo/categorias", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar categorías de servicios GestoPago", description = "Agrupa el catálogo por idCatTipoServicio indicando total de productos y servicios incluidos")
    public ResponseEntity<List<CatalogoCategoriaResponse>> obtenerCategorias() {
        return ResponseEntity.ok(catalogoService.obtenerCategorias());
    }

    @GetMapping(value = "/gestopago/catalogo/{idServicio}/{idProducto}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar detalle de producto GestoPago", description = "Retorna precio, campos requeridos, tipo de referencia y leyendas del producto seleccionado")
    public ResponseEntity<CatalogoProducto> obtenerDetalleProducto(
            @PathVariable("idServicio") String idServicio,
            @PathVariable("idProducto") String idProducto) {
        return ResponseEntity.ok(catalogoService.obtenerDetalleProducto(idServicio, idProducto));
    }

    @GetMapping(value = "/gestopago/servicios/{idServicio}/logo")
    @Operation(summary = "Obtener logo PNG del servicio GestoPago", description = "Descarga de forma segura el logo del servicio desde GestoPago usando autenticación del servidor")
    public ResponseEntity<byte[]> obtenerLogoServicio(
            @PathVariable("idServicio") String idServicio,
            HttpServletRequest httpRequest) {
        String ipOrigen = ClientIpResolver.resolverIpOrigen(httpRequest, trustForwardedHeaders);
        publicEndpointRateLimiter.verificarLimiteImagenes(ipOrigen);
        return construirRespuestaImagen(gestoPagoImagenService.obtenerLogoServicio(idServicio));
    }

    @GetMapping(value = "/gestopago/servicios/{idServicio}/referencia")
    @Operation(summary = "Obtener imagen de ayuda/referencia del servicio GestoPago", description = "Descarga la imagen guía de referencia del servicio cuando está disponible en GestoPago")
    public ResponseEntity<byte[]> obtenerImagenReferencia(
            @PathVariable("idServicio") String idServicio,
            HttpServletRequest httpRequest) {
        String ipOrigen = ClientIpResolver.resolverIpOrigen(httpRequest, trustForwardedHeaders);
        publicEndpointRateLimiter.verificarLimiteImagenes(ipOrigen);
        return construirRespuestaImagen(gestoPagoImagenService.obtenerImagenReferencia(idServicio));
    }

    private ResponseEntity<byte[]> construirRespuestaImagen(Optional<ImagenRecurso> recursoOpt) {
        return recursoOpt
                .map(recurso -> ResponseEntity.ok()
                        .contentType(recurso.mediaType())
                        .cacheControl(CacheControl.maxAge(CACHE_IMAGEN_HORAS, TimeUnit.HOURS).cachePublic())
                        .body(recurso.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}