package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.CatalogoCategoriaResponse;
import com.proyecto.servicios.model.gestopago.catproduct.GestoPagoCatProductResponse;
import com.proyecto.servicios.model.gestopago.catproduct.Producto;
import com.proyecto.servicios.repositorys.gestopago.CatalogoProductoRepository;
import com.proyecto.servicios.service.CatalogoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CatalogoServiceImpl implements CatalogoService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CATEGORIA_SIN_CLASIFICAR = "SIN_CATEGORIA";

    private final CatalogoProductoRepository catalogoProductoRepository;

    public CatalogoServiceImpl(CatalogoProductoRepository catalogoProductoRepository) {
        this.catalogoProductoRepository = catalogoProductoRepository;
    }

    @Override
    public void reemplazarCatalogo(GestoPagoCatProductResponse response) {
        if (response == null || response.getProductos() == null
                || response.getProductos().getProductos() == null) {
            throw new IllegalArgumentException("El catalogo a persistir no puede ser nulo");
        }

        List<Producto> productos = response.getProductos().getProductos();
        if (productos.isEmpty()) {
            throw new IllegalArgumentException("El catalogo a persistir no puede estar vacio");
        }

        List<CatalogoProducto> documentos = productos.stream()
                .map(this::aDocumento)
                .toList();
        List<String> idsActuales = documentos.stream()
                .map(CatalogoProducto::getId)
                .distinct()
                .toList();

        catalogoProductoRepository.saveAll(documentos);
        catalogoProductoRepository.deleteByIdNotIn(idsActuales);
        log.info("Catalogo GestoPago actualizado sin ventana vacia en MongoDB ({} productos)", documentos.size());
    }

    @Override
    public List<CatalogoProducto> obtenerCatalogo() {
        return catalogoProductoRepository.findAll();
    }

    @Override
    public List<CatalogoProducto> buscarCatalogo(String categoria, String servicio, String query) {
        String categoriaNorm = normalizarFiltro(categoria);
        String servicioNorm = normalizarFiltro(servicio);
        String queryNorm = normalizarFiltro(query);

        return catalogoProductoRepository.findAll().stream()
                .filter(prod -> coincideCategoria(prod, categoriaNorm))
                .filter(prod -> coincideServicio(prod, servicioNorm))
                .filter(prod -> coincideTexto(prod, queryNorm))
                .sorted(Comparator
                        .comparing((CatalogoProducto p) -> textoSeguro(p.getServicio()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> textoSeguro(p.getNombreProducto()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public PageResponse<CatalogoProducto> buscarCatalogoPaginado(
            String categoria, String servicio, String query, int page, int size) {
        if (page < 0) {
            throw new ReglaNegocioException("El numero de pagina no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReglaNegocioException("El tamano de pagina debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        List<CatalogoProducto> filtrados = buscarCatalogo(categoria, servicio, query);
        int inicio = Math.min(page * size, filtrados.size());
        int fin = Math.min(inicio + size, filtrados.size());
        List<CatalogoProducto> contenido = filtrados.subList(inicio, fin);
        return PageResponse.from(new PageImpl<>(contenido, PageRequest.of(page, size), filtrados.size()));
    }

    @Override
    public List<CatalogoCategoriaResponse> obtenerCategorias() {
        Map<String, List<CatalogoProducto>> agrupados = catalogoProductoRepository.findAll().stream()
                .collect(Collectors.groupingBy(prod -> resolverCategoria(prod.getIdCatTipoServicio())));

        return agrupados.entrySet().stream()
                .map(entry -> {
                    List<String> servicios = entry.getValue().stream()
                            .map(CatalogoProducto::getServicio)
                            .filter(s -> s != null && !s.isBlank())
                            .map(String::trim)
                            .distinct()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    return CatalogoCategoriaResponse.builder()
                            .idCatTipoServicio(entry.getKey())
                            .totalProductos(entry.getValue().size())
                            .servicios(servicios)
                            .build();
                })
                .sorted(Comparator.comparing(CatalogoCategoriaResponse::getIdCatTipoServicio))
                .toList();
    }

    @Override
    public CatalogoProducto obtenerDetalleProducto(String idServicio, String idProducto) {
        String servicioLimpio = normalizarIdNumerico(idServicio, "idServicio");
        String productoLimpio = normalizarIdNumerico(idProducto, "idProducto");
        return catalogoProductoRepository.findFirstByIdServicioAndIdProducto(servicioLimpio, productoLimpio)
                .orElseThrow(() -> new CatalogoNotFoundException(
                        "No se encontro el producto " + productoLimpio + " del servicio " + servicioLimpio + " en el catalogo."));
    }

    @Override
    public CatalogoProducto validarYObtenerProducto(Integer idServicio, Integer idProducto) {
        String idServicioStr = String.valueOf(idServicio);
        String idProductoStr = String.valueOf(idProducto);

        if (catalogoProductoRepository.count() == 0) {
            throw new CatalogoNotFoundException(
                    "El catalogo de servicios no se encuentra cargado o disponible en este momento.");
        }

        if (!catalogoProductoRepository.existsByIdServicio(idServicioStr)) {
            throw new ReglaNegocioException(
                    "El servicio solicitado (" + idServicio + ") no existe en el catalogo autorizado.");
        }

        return catalogoProductoRepository.findFirstByIdServicioAndIdProducto(idServicioStr, idProductoStr)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El producto solicitado (" + idProducto + ") no existe o no pertenece al servicio " + idServicio + "."));
    }

    private CatalogoProducto aDocumento(Producto producto) {
        CatalogoProducto documento = new CatalogoProducto();
        documento.setId(construirIdCompuesto(producto.getIdServicio(), producto.getIdProducto()));
        documento.setServicio(producto.getServicio());
        documento.setNombreProducto(producto.getNombreProducto());
        documento.setIdServicio(producto.getIdServicio());
        documento.setIdProducto(producto.getIdProducto());
        documento.setIdCatTipoServicio(producto.getIdCatTipoServicio());
        documento.setTipoFront(producto.getTipoFront());
        documento.setHasDigitoVerificador(producto.getHasDigitoVerificador());
        documento.setPrecio(producto.getPrecio());
        documento.setShowAyuda(producto.getShowAyuda());
        documento.setTipoReferencia(producto.getTipoReferencia());
        documento.setLegend(producto.getLegend());
        return documento;
    }

    private static String construirIdCompuesto(String idServicio, String idProducto) {
        if (idServicio != null && !idServicio.isBlank() && idProducto != null && !idProducto.isBlank()) {
            return idServicio.trim() + ":" + idProducto.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static String resolverCategoria(String categoria) {
        return (categoria == null || categoria.isBlank()) ? CATEGORIA_SIN_CLASIFICAR : categoria.trim();
    }

    private static String normalizarFiltro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean coincideCategoria(CatalogoProducto prod, String categoriaNorm) {
        if (categoriaNorm == null) {
            return true;
        }
        return resolverCategoria(prod.getIdCatTipoServicio()).toLowerCase(Locale.ROOT).equals(categoriaNorm);
    }

    private static boolean coincideServicio(CatalogoProducto prod, String servicioNorm) {
        if (servicioNorm == null) {
            return true;
        }
        return textoSeguro(prod.getIdServicio()).toLowerCase(Locale.ROOT).equals(servicioNorm)
                || textoSeguro(prod.getServicio()).toLowerCase(Locale.ROOT).contains(servicioNorm);
    }

    private static boolean coincideTexto(CatalogoProducto prod, String queryNorm) {
        if (queryNorm == null) {
            return true;
        }
        return textoSeguro(prod.getServicio()).toLowerCase(Locale.ROOT).contains(queryNorm)
                || textoSeguro(prod.getNombreProducto()).toLowerCase(Locale.ROOT).contains(queryNorm)
                || textoSeguro(prod.getLegend()).toLowerCase(Locale.ROOT).contains(queryNorm);
    }

    private static String textoSeguro(String valor) {
        return Objects.toString(valor, "");
    }

    private static String normalizarIdNumerico(String valor, String campo) {
        if (valor == null || valor.isBlank() || !valor.trim().matches("^[1-9]\\d{0,9}$")) {
            throw new ReglaNegocioException("El parametro " + campo + " debe ser un entero positivo valido.");
        }
        return valor.trim();
    }
}
