package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.CatalogoCategoriaResponse;
import com.proyecto.servicios.model.gestopago.catproduct.GestoPagoCatProductResponse;

import java.util.List;

public interface CatalogoService {

    void reemplazarCatalogo(GestoPagoCatProductResponse response);

    List<CatalogoProducto> obtenerCatalogo();

    List<CatalogoProducto> buscarCatalogo(String categoria, String servicio, String query);

    PageResponse<CatalogoProducto> buscarCatalogoPaginado(String categoria, String servicio, String query, int page, int size);

    List<CatalogoCategoriaResponse> obtenerCategorias();

    CatalogoProducto obtenerDetalleProducto(String idServicio, String idProducto);

    CatalogoProducto validarYObtenerProducto(Integer idServicio, Integer idProducto);
}
