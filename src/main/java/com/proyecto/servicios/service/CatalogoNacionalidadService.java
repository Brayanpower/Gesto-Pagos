package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadRequest;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;

import java.util.List;

public interface CatalogoNacionalidadService {

    List<CatalogoNacionalidadResponse> obtenerCatalogo();

    CatalogoNacionalidadResponse obtenerPorId(Long id);

    List<CatalogoNacionalidadResponse> buscarPorNombre(String nombre);

    CatalogoNacionalidadResponse crear(CatalogoNacionalidadRequest request);

    CatalogoNacionalidadResponse actualizar(Long id, CatalogoNacionalidadRequest request);

    void eliminar(Long id);
}
