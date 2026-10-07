package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoPaisRequest;
import com.proyecto.servicios.model.catalogos.CatalogoPaisResponse;

import java.util.List;

public interface CatalogoPaisService {

    List<CatalogoPaisResponse> obtenerCatalogo();

    CatalogoPaisResponse obtenerPorId(Long id);

    List<CatalogoPaisResponse> buscarPorNombre(String nombre);

    CatalogoPaisResponse crear(CatalogoPaisRequest request);

    CatalogoPaisResponse actualizar(Long id, CatalogoPaisRequest request);

    void eliminar(Long id);
}
