package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoColonia;
import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.entity.catalogos.CatalogoNacionalidad;
import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.mapper.CatalogoColoniaMapper;
import com.proyecto.servicios.mapper.CatalogoEstadoCivilMapper;
import com.proyecto.servicios.mapper.CatalogoEstadoMapper;
import com.proyecto.servicios.mapper.CatalogoMunicipioMapper;
import com.proyecto.servicios.mapper.CatalogoNacionalidadMapper;
import com.proyecto.servicios.mapper.CatalogoPaisMapper;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaRequest;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaResponse;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoResponse;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioRequest;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioResponse;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadRequest;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;
import com.proyecto.servicios.model.catalogos.CatalogoPaisRequest;
import com.proyecto.servicios.model.catalogos.CatalogoPaisResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoColoniaRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoCivilRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoMunicipioRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoNacionalidadRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoPaisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de servicios CRUD de catalogos geograficos y personales")
class CatalogosCrudServicesTest {

    @Mock private CatalogoColoniaRepository coloniaRepo;
    @Mock private CatalogoMunicipioRepository municipioRepo;
    @Mock private CatalogoEstadoRepository estadoRepo;
    @Mock private CatalogoPaisRepository paisRepo;
    @Mock private CatalogoEstadoCivilRepository estadoCivilRepo;
    @Mock private CatalogoNacionalidadRepository nacionalidadRepo;

    @Mock private CatalogoColoniaMapper coloniaMapper;
    @Mock private CatalogoMunicipioMapper municipioMapper;
    @Mock private CatalogoEstadoMapper estadoMapper;
    @Mock private CatalogoPaisMapper paisMapper;
    @Mock private CatalogoEstadoCivilMapper estadoCivilMapper;
    @Mock private CatalogoNacionalidadMapper nacionalidadMapper;

    @Test
    @DisplayName("CatalogoColoniaServiceImpl crea, consulta y valida municipio existente")
    void coloniaService_crudBasico() {
        CatalogoColoniaServiceImpl service = new CatalogoColoniaServiceImpl(coloniaRepo, municipioRepo, coloniaMapper);
        CatalogoMunicipio mun = new CatalogoMunicipio();
        mun.setId(2L);
        CatalogoColonia col = new CatalogoColonia();
        col.setId(10L);
        CatalogoColoniaResponse resp = new CatalogoColoniaResponse();
        resp.setId(10L);

        when(municipioRepo.findById(2L)).thenReturn(Optional.of(mun));
        when(coloniaMapper.toEntity(any())).thenReturn(col);
        when(coloniaRepo.save(col)).thenReturn(col);
        when(coloniaMapper.toResponse(col)).thenReturn(resp);
        when(coloniaRepo.findByCatalogoMunicipioId(2L)).thenReturn(List.of(col));
        when(coloniaMapper.toResponses(List.of(col))).thenReturn(List.of(resp));

        CatalogoColoniaRequest req = new CatalogoColoniaRequest();
        req.setColonia("Centro");
        req.setMunicipioId(2L);

        assertEquals(10L, service.crear(req).getId());
        assertEquals(1, service.buscarPorMunicipio(2L).size());

        when(coloniaRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(CatalogoNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    @DisplayName("CatalogoMunicipioServiceImpl crea y valida estado padre")
    void municipioService_crudBasico() {
        CatalogoMunicipioServiceImpl service = new CatalogoMunicipioServiceImpl(municipioRepo, estadoRepo, municipioMapper);
        CatalogoEstado est = new CatalogoEstado();
        est.setId(3L);
        CatalogoMunicipio mun = new CatalogoMunicipio();
        mun.setId(20L);
        CatalogoMunicipioResponse resp = new CatalogoMunicipioResponse();
        resp.setId(20L);

        when(estadoRepo.findById(3L)).thenReturn(Optional.of(est));
        when(municipioMapper.toEntity(any())).thenReturn(mun);
        when(municipioRepo.save(mun)).thenReturn(mun);
        when(municipioMapper.toResponse(mun)).thenReturn(resp);

        CatalogoMunicipioRequest req = new CatalogoMunicipioRequest();
        req.setMunicipio("Benito Juarez");
        req.setEstadoId(3L);
        assertEquals(20L, service.crear(req).getId());

        when(estadoRepo.findById(99L)).thenReturn(Optional.empty());
        req.setEstadoId(99L);
        assertThrows(CatalogoNotFoundException.class, () -> service.crear(req));
    }

    @Test
    @DisplayName("CatalogoEstadoServiceImpl crea y valida pais padre")
    void estadoService_crudBasico() {
        CatalogoEstadoServiceImpl service = new CatalogoEstadoServiceImpl(estadoRepo, paisRepo, estadoMapper);
        CatalogoPais pais = new CatalogoPais();
        pais.setId(1L);
        CatalogoEstado est = new CatalogoEstado();
        est.setId(30L);
        CatalogoEstadoResponse resp = new CatalogoEstadoResponse();
        resp.setId(30L);

        when(paisRepo.findById(1L)).thenReturn(Optional.of(pais));
        when(estadoMapper.toEntity(any())).thenReturn(est);
        when(estadoRepo.save(est)).thenReturn(est);
        when(estadoMapper.toResponse(est)).thenReturn(resp);

        CatalogoEstadoRequest req = new CatalogoEstadoRequest();
        req.setEstado("CDMX");
        req.setPaisId(1L);
        assertEquals(30L, service.crear(req).getId());

        when(estadoRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(CatalogoNotFoundException.class, () -> service.eliminar(99L));
    }

    @Test
    @DisplayName("CatalogoPais, EstadoCivil y Nacionalidad rechazan duplicados con DuplicateResourceException")
    void catalogosSimples_rechazanDuplicadosYCrean() {
        CatalogoPaisServiceImpl paisService = new CatalogoPaisServiceImpl(paisRepo, paisMapper);
        CatalogoPais pais = new CatalogoPais();
        pais.setId(1L);
        CatalogoPaisResponse paisResp = new CatalogoPaisResponse();
        paisResp.setId(1L);
        when(paisRepo.findByNombre("Mexico")).thenReturn(Optional.empty()).thenReturn(Optional.of(pais));
        when(paisMapper.toEntity(any())).thenReturn(pais);
        when(paisRepo.save(pais)).thenReturn(pais);
        when(paisMapper.toResponse(pais)).thenReturn(paisResp);

        CatalogoPaisRequest paisReq = new CatalogoPaisRequest();
        paisReq.setNombre("Mexico");
        assertEquals(1L, paisService.crear(paisReq).getId());
        assertThrows(DuplicateResourceException.class, () -> paisService.crear(paisReq));

        CatalogoEstadoCivilServiceImpl ecService = new CatalogoEstadoCivilServiceImpl(estadoCivilRepo, estadoCivilMapper);
        CatalogoEstadoCivil ec = new CatalogoEstadoCivil();
        ec.setId(2L);
        CatalogoEstadoCivilResponse ecResp = new CatalogoEstadoCivilResponse();
        ecResp.setId(2L);
        when(estadoCivilRepo.findByNombre("Soltero")).thenReturn(Optional.empty()).thenReturn(Optional.of(ec));
        when(estadoCivilMapper.toEntity(any())).thenReturn(ec);
        when(estadoCivilRepo.save(ec)).thenReturn(ec);
        when(estadoCivilMapper.toResponse(ec)).thenReturn(ecResp);

        CatalogoEstadoCivilRequest ecReq = new CatalogoEstadoCivilRequest();
        ecReq.setNombre("Soltero");
        assertEquals(2L, ecService.crear(ecReq).getId());
        assertThrows(DuplicateResourceException.class, () -> ecService.crear(ecReq));

        CatalogoNacionalidadServiceImpl nacService = new CatalogoNacionalidadServiceImpl(nacionalidadRepo, nacionalidadMapper);
        CatalogoNacionalidad nac = new CatalogoNacionalidad();
        nac.setId(3L);
        CatalogoNacionalidadResponse nacResp = new CatalogoNacionalidadResponse();
        nacResp.setId(3L);
        when(nacionalidadRepo.findByNombre("Mexicana")).thenReturn(Optional.empty()).thenReturn(Optional.of(nac));
        when(nacionalidadMapper.toEntity(any())).thenReturn(nac);
        when(nacionalidadRepo.save(nac)).thenReturn(nac);
        when(nacionalidadMapper.toResponse(nac)).thenReturn(nacResp);

        CatalogoNacionalidadRequest nacReq = new CatalogoNacionalidadRequest();
        nacReq.setNombre("Mexicana");
        assertEquals(3L, nacService.crear(nacReq).getId());
        assertThrows(DuplicateResourceException.class, () -> nacService.crear(nacReq));

        verify(paisRepo).save(pais);
    }
}
