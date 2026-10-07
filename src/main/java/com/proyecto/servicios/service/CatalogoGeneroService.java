package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoGeneroRequest;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;

import java.util.List;

public interface CatalogoGeneroService {

    List<CatalogoGeneroResponse> obtenerCatalogo();

    CatalogoGeneroResponse obtenerPorId(Long id);

    List<CatalogoGeneroResponse> buscarPorTipo(String tipo);

    CatalogoGeneroResponse crear(CatalogoGeneroRequest request);

    CatalogoGeneroResponse actualizar(Long id, CatalogoGeneroRequest request);

    void eliminar(Long id);
}
