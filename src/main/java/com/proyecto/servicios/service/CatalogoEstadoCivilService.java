package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;

import java.util.List;

public interface CatalogoEstadoCivilService {

    List<CatalogoEstadoCivilResponse> obtenerCatalogo();

    CatalogoEstadoCivilResponse obtenerPorId(Long id);

    List<CatalogoEstadoCivilResponse> buscarPorNombre(String nombre);

    CatalogoEstadoCivilResponse crear(CatalogoEstadoCivilRequest request);

    CatalogoEstadoCivilResponse actualizar(Long id, CatalogoEstadoCivilRequest request);

    void eliminar(Long id);
}
