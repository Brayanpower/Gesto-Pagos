package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.gestopago.catproduct.GestoPagoCatProductResponse;
import com.proyecto.servicios.model.gestopago.catproduct.Producto;
import com.proyecto.servicios.repositorys.gestopago.CatalogoProductoRepository;
import com.proyecto.servicios.service.CatalogoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CatalogoServiceImpl implements CatalogoService {

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
}
