package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.mapper.CatalogoMunicipioMapper;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioRequest;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoMunicipioRepository;
import com.proyecto.servicios.service.CatalogoMunicipioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CatalogoMunicipioServiceImpl implements CatalogoMunicipioService {

    private final CatalogoMunicipioRepository repository;
    private final CatalogoEstadoRepository estadoRepository;
    private final CatalogoMunicipioMapper mapper;

    public CatalogoMunicipioServiceImpl(CatalogoMunicipioRepository repository,
                                       CatalogoEstadoRepository estadoRepository,
                                       CatalogoMunicipioMapper mapper) {
        this.repository = repository;
        this.estadoRepository = estadoRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoMunicipioResponse> obtenerCatalogo() {
        return mapper.toResponses(repository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoMunicipioResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoMunicipioResponse> buscarPorEstado(Long estadoId) {
        if (estadoId == null) {
            return obtenerCatalogo();
        }
        return mapper.toResponses(repository.findByCatalogoEstadoId(estadoId));
    }

    @Override
    @Transactional
    public CatalogoMunicipioResponse crear(CatalogoMunicipioRequest request) {
        CatalogoEstado estado = estadoRepository.findById(request.getEstadoId())
                .orElseThrow(() -> new CatalogoNotFoundException("El estado con id " + request.getEstadoId() + " no existe"));

        CatalogoMunicipio entidad = mapper.toEntity(request);
        entidad.setMunicipio(request.getMunicipio().trim());
        entidad.setCatalogoEstado(estado);
        CatalogoMunicipio guardada = repository.save(entidad);
        log.info("Municipio creado con id {}", guardada.getId());
        return mapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public CatalogoMunicipioResponse actualizar(Long id, CatalogoMunicipioRequest request) {
        CatalogoMunicipio entidad = buscar(id);
        CatalogoEstado estado = estadoRepository.findById(request.getEstadoId())
                .orElseThrow(() -> new CatalogoNotFoundException("El estado con id " + request.getEstadoId() + " no existe"));

        mapper.updateEntity(request, entidad);
        entidad.setMunicipio(request.getMunicipio().trim());
        entidad.setCatalogoEstado(estado);
        CatalogoMunicipio actualizada = repository.save(entidad);
        log.info("Municipio actualizado con id {}", id);
        return mapper.toResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CatalogoMunicipio entidad = buscar(id);
        repository.delete(entidad);
        log.info("Municipio eliminado con id {}", id);
    }

    private CatalogoMunicipio buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogoNotFoundException("El municipio con id " + id + " no existe"));
    }
}
