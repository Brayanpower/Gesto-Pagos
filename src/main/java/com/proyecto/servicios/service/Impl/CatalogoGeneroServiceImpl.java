package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.mapper.CatalogoGeneroMapper;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroRequest;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoGenerosRepository;
import com.proyecto.servicios.service.CatalogoGeneroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoGeneroServiceImpl implements CatalogoGeneroService {

    private final CatalogoGenerosRepository catalogoGenerosRepository;
    private final CatalogoGeneroMapper catalogoGeneroMapper;

    public CatalogoGeneroServiceImpl(CatalogoGenerosRepository catalogoGenerosRepository,
                                    CatalogoGeneroMapper catalogoGeneroMapper) {
        this.catalogoGenerosRepository = catalogoGenerosRepository;
        this.catalogoGeneroMapper = catalogoGeneroMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoGeneroResponse> obtenerCatalogo() {
        return catalogoGeneroMapper.toResponses(catalogoGenerosRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoGeneroResponse obtenerPorId(Long id) {
        return catalogoGeneroMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoGeneroResponse> buscarPorTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return obtenerCatalogo();
        }
        return catalogoGeneroMapper.toResponses(
                catalogoGenerosRepository.findByTipoContainingIgnoreCaseOrderByTipoAsc(tipo.trim()));
    }

    @Override
    @Transactional
    public CatalogoGeneroResponse crear(CatalogoGeneroRequest request) {
        if (request == null || request.getTipo() == null || request.getTipo().isBlank()) {
            throw new IllegalArgumentException("El tipo de genero es obligatorio");
        }
        String tipo = request.getTipo().trim();
        if (catalogoGenerosRepository.findByTipo(tipo).isPresent()) {
            throw new IllegalArgumentException("El tipo de genero ya existe en el catalogo: " + tipo);
        }
        CatalogoGeneros entidad = catalogoGeneroMapper.toEntity(request);
        entidad.setTipo(tipo);
        CatalogoGeneros guardada = catalogoGenerosRepository.save(entidad);
        log.info("Catalogo de genero creado con id {}", guardada.getId());
        return catalogoGeneroMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoGeneroResponse actualizar(Long id, CatalogoGeneroRequest request) {
        CatalogoGeneros entidad = buscar(id);
        if (request == null || request.getTipo() == null || request.getTipo().isBlank()) {
            throw new IllegalArgumentException("El tipo de genero es obligatorio");
        }
        String tipo = request.getTipo().trim();
        catalogoGenerosRepository.findByTipo(tipo)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("El tipo de genero ya existe en el catalogo: " + tipo);
                });
        catalogoGeneroMapper.updateEntity(request, entidad);
        entidad.setTipo(tipo);
        CatalogoGeneros actualizada = catalogoGenerosRepository.save(entidad);
        log.info("Catalogo de genero actualizado con id {}", id);
        return catalogoGeneroMapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoGeneros entidad = buscar(id);
        catalogoGenerosRepository.delete(entidad);
        log.info("Catalogo de genero eliminado con id {}", id);
    }

    private CatalogoGeneros buscar(Long id) {
        return catalogoGenerosRepository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException(
                        "El catalogo de genero con id " + id + " no existe"));
    }
}
