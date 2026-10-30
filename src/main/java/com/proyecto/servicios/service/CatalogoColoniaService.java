package com.proyecto.servicios.service;

import com.proyecto.servicios.model.catalogos.CatalogoColoniaRequest;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaResponse;

import java.util.List;

public interface CatalogoColoniaService {

    List<CatalogoColoniaResponse> obtenerCatalogo();

    CatalogoColoniaResponse obtenerPorId(Long id);

    List<CatalogoColoniaResponse> buscarPorMunicipio(Long municipioId);

    CatalogoColoniaResponse crear(CatalogoColoniaRequest request);

    CatalogoColoniaResponse actualizar(Long id, CatalogoColoniaRequest request);

    void eliminar(Long id);
}
