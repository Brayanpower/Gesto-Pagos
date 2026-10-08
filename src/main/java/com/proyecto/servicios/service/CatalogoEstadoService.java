package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoEstadoRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoResponse;

import java.util.List;

public interface CatalogoEstadoService {

    List<CatalogoEstadoResponse> obtenerCatalogo();

    CatalogoEstadoResponse obtenerPorId(Long id);

    List<CatalogoEstadoResponse> buscarPorPais(Long paisId);

    CatalogoEstadoResponse crear(CatalogoEstadoRequest request);

    CatalogoEstadoResponse actualizar(Long id, CatalogoEstadoRequest request);

    void eliminar(Long id);
}
