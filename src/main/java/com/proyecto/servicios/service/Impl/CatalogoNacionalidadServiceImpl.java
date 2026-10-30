package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoNacionalidad;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.mapper.CatalogoNacionalidadMapper;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadRequest;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoNacionalidadRepository;
import com.proyecto.servicios.service.CatalogoNacionalidadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoNacionalidadServiceImpl implements CatalogoNacionalidadService {

    private final CatalogoNacionalidadRepository repository;
    private final CatalogoNacionalidadMapper mapper;

    public CatalogoNacionalidadServiceImpl(CatalogoNacionalidadRepository repository,
                                          CatalogoNacionalidadMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoNacionalidadResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoNacionalidadResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoNacionalidadResponse> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre.trim()));
    }

    @Override
    @Transactional
    public CatalogoNacionalidadResponse crear(CatalogoNacionalidadRequest request) {
        String nombre = request.getNombre().trim();
        if (repository.findByNombre(nombre).isPresent()) {
            throw new DuplicateResourceException("La nacionalidad ya existe en el catalogo: " + nombre);
        }
        CatalogoNacionalidad entidad = mapper.toEntity(request);
        entidad.setNombre(nombre);
        CatalogoNacionalidad guardada = repository.save(entidad);
        log.info("Nacionalidad creada con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoNacionalidadResponse actualizar(Long id, CatalogoNacionalidadRequest request) {
        CatalogoNacionalidad entidad = buscar(id);
        String nombre = request.getNombre().trim();
        repository.findByNombre(nombre)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("La nacionalidad ya existe en el catalogo: " + nombre);
                });
        mapper.updateEntity(request, entidad);
        entidad.setNombre(nombre);
        CatalogoNacionalidad actualizada = repository.save(entidad);
        log.info("Nacionalidad actualizada con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoNacionalidad entidad = buscar(id);
        repository.delete(entidad);
        log.info("Nacionalidad eliminada con id {}", id);
    }

    private CatalogoNacionalidad buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("La nacionalidad con id " + id + " no existe"));
    }
}
