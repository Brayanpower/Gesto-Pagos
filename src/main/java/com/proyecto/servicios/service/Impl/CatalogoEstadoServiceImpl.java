package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.mapper.CatalogoEstadoMapper;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoPaisRepository;
import com.proyecto.servicios.service.CatalogoEstadoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoEstadoServiceImpl implements CatalogoEstadoService {

    private final CatalogoEstadoRepository repository;
    private final CatalogoPaisRepository paisRepository;
    private final CatalogoEstadoMapper mapper;

    public CatalogoEstadoServiceImpl(CatalogoEstadoRepository repository,
                                    CatalogoPaisRepository paisRepository,
                                    CatalogoEstadoMapper mapper) {
        this.repository = repository;
        this.paisRepository = paisRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoEstadoResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoEstadoResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoEstadoResponse> buscarPorPais(Long paisId) {
        if (paisId == null) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByCatalogoPaisId(paisId));
    }

    @Override
    @Transactional
    public CatalogoEstadoResponse crear(CatalogoEstadoRequest request) {
        CatalogoPais pais = paisRepository.findById(request.getPaisId())
                .orElseThrow(() -> new CatalogoNotFoundException("El pais con id " + request.getPaisId() + " no existe"));

        CatalogoEstado entidad = mapper.toEntity(request);
        entidad.setEstado(request.getEstado().trim());
        entidad.setCatalogoPais(pais);
        CatalogoEstado guardada = repository.save(entidad);
        log.info("Estado creado con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoEstadoResponse actualizar(Long id, CatalogoEstadoRequest request) {
        CatalogoEstado entidad = buscar(id);
        CatalogoPais pais = paisRepository.findById(request.getPaisId())
                .orElseThrow(() -> new CatalogoNotFoundException("El pais con id " + request.getPaisId() + " no existe"));

        mapper.updateEntity(request, entidad);
        entidad.setEstado(request.getEstado().trim());
        entidad.setCatalogoPais(pais);
        CatalogoEstado actualizada = repository.save(entidad);
        log.info("Estado actualizado con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoEstado entidad = buscar(id);
        repository.delete(entidad);
        log.info("Estado eliminado con id {}", id);
    }

    private CatalogoEstado buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("El estado con id " + id + " no existe"));
    }
}
