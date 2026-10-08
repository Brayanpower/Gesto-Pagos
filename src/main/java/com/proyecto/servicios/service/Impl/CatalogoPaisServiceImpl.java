package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.mapper.CatalogoPaisMapper;
import com.proyecto.servicios.model.catalogos.CatalogoPaisRequest;
import com.proyecto.servicios.model.catalogos.CatalogoPaisResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoPaisRepository;
import com.proyecto.servicios.service.CatalogoPaisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoPaisServiceImpl implements CatalogoPaisService {

    private final CatalogoPaisRepository repository;
    private final CatalogoPaisMapper mapper;

    public CatalogoPaisServiceImpl(CatalogoPaisRepository repository,
                                  CatalogoPaisMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoPaisResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoPaisResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoPaisResponse> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre.trim()));
    }

    @Override
    @Transactional
    public CatalogoPaisResponse crear(CatalogoPaisRequest request) {
        String nombre = request.getNombre().trim();
        if (repository.findByNombre(nombre).isPresent()) {
            throw new DuplicateResourceException("El pais ya existe en el catalogo: " + nombre);
        }
        CatalogoPais entidad = mapper.toEntity(request);
        entidad.setNombre(nombre);
        CatalogoPais guardada = repository.save(entidad);
        log.info("Pais creado con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoPaisResponse actualizar(Long id, CatalogoPaisRequest request) {
        CatalogoPais entidad = buscar(id);
        String nombre = request.getNombre().trim();
        repository.findByNombre(nombre)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("El pais ya existe en el catalogo: " + nombre);
                });
        mapper.updateEntity(request, entidad);
        entidad.setNombre(nombre);
        CatalogoPais actualizada = repository.save(entidad);
        log.info("Pais actualizado con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoPais entidad = buscar(id);
        repository.delete(entidad);
        log.info("Pais eliminado con id {}", id);
    }

    private CatalogoPais buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("El pais con id " + id + " no existe"));
    }
}
