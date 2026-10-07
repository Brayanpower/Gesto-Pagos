package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoMunicipioRequest;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioResponse;

import java.util.List;

public interface CatalogoMunicipioService {

    List<CatalogoMunicipioResponse> obtenerCatalogo();

    CatalogoMunicipioResponse obtenerPorId(Long id);

    List<CatalogoMunicipioResponse> buscarPorEstado(Long estadoId);

    CatalogoMunicipioResponse crear(CatalogoMunicipioRequest request);

    CatalogoMunicipioResponse actualizar(Long id, CatalogoMunicipioRequest request);

    void eliminar(Long id);
}
