package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.mapper.CatalogoEstadoCivilMapper;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoCivilRepository;
import com.proyecto.servicios.service.CatalogoEstadoCivilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoEstadoCivilServiceImpl implements CatalogoEstadoCivilService {

    private final CatalogoEstadoCivilRepository repository;
    private final CatalogoEstadoCivilMapper mapper;

    public CatalogoEstadoCivilServiceImpl(CatalogoEstadoCivilRepository repository,
                                         CatalogoEstadoCivilMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoEstadoCivilResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoEstadoCivilResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoEstadoCivilResponse> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre.trim()));
    }

    @Override
    @Transactional
    public CatalogoEstadoCivilResponse crear(CatalogoEstadoCivilRequest request) {
        String nombre = request.getNombre().trim();
        if (repository.findByNombre(nombre).isPresent()) {
            throw new DuplicateResourceException("El estado civil ya existe en el catalogo: " + nombre);
        }
        CatalogoEstadoCivil entidad = mapper.toEntity(request);
        entidad.setNombre(nombre);
        CatalogoEstadoCivil guardada = repository.save(entidad);
        log.info("Estado civil creado con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoEstadoCivilResponse actualizar(Long id, CatalogoEstadoCivilRequest request) {
        CatalogoEstadoCivil entidad = buscar(id);
        String nombre = request.getNombre().trim();
        repository.findByNombre(nombre)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("El estado civil ya existe en el catalogo: " + nombre);
                });
        mapper.updateEntity(request, entidad);
        entidad.setNombre(nombre);
        CatalogoEstadoCivil actualizada = repository.save(entidad);
        log.info("Estado civil actualizado con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoEstadoCivil entidad = buscar(id);
        repository.delete(entidad);
        log.info("Estado civil eliminado con id {}", id);
    }

    private CatalogoEstadoCivil buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("El estado civil con id " + id + " no existe"));
    }
}
