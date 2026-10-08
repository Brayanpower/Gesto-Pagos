package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoColonia;
import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.mapper.CatalogoColoniaMapper;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaRequest;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoColoniaRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoMunicipioRepository;
import com.proyecto.servicios.service.CatalogoColoniaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoColoniaServiceImpl implements CatalogoColoniaService {

    private final CatalogoColoniaRepository repository;
    private final CatalogoMunicipioRepository municipioRepository;
    private final CatalogoColoniaMapper mapper;

    public CatalogoColoniaServiceImpl(CatalogoColoniaRepository repository,
                                     CatalogoMunicipioRepository municipioRepository,
                                     CatalogoColoniaMapper mapper) {
        this.repository = repository;
        this.municipioRepository = municipioRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoColoniaResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoColoniaResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoColoniaResponse> buscarPorMunicipio(Long municipioId) {
        if (municipioId == null) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByCatalogoMunicipioId(municipioId));
    }

    @Override
    @Transactional
    public CatalogoColoniaResponse crear(CatalogoColoniaRequest request) {
        CatalogoMunicipio municipio = municipioRepository.findById(request.getMunicipioId())
                .orElseThrow(() -> new CatalogoNotFoundException("El municipio con id " + request.getMunicipioId() + " no existe"));

        CatalogoColonia entidad = mapper.toEntity(request);
        entidad.setColonia(request.getColonia().trim());
        entidad.setCatalogoMunicipio(municipio);
        CatalogoColonia guardada = repository.save(entidad);
        log.info("Colonia creada con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoColoniaResponse actualizar(Long id, CatalogoColoniaRequest request) {
        CatalogoColonia entidad = buscar(id);
        CatalogoMunicipio municipio = municipioRepository.findById(request.getMunicipioId())
                .orElseThrow(() -> new CatalogoNotFoundException("El municipio con id " + request.getMunicipioId() + " no existe"));

        mapper.updateEntity(request, entidad);
        entidad.setColonia(request.getColonia().trim());
        entidad.setCatalogoMunicipio(municipio);
        CatalogoColonia actualizada = repository.save(entidad);
        log.info("Colonia actualizada con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoColonia entidad = buscar(id);
        repository.delete(entidad);
        log.info("Colonia eliminada con id {}", id);
    }

    private CatalogoColonia buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("La colonia con id " + id + " no existe"));
    }
}
