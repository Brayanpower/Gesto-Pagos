package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.mapper.CatalogoGeneroMapper;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroRequest;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;
import com.proyecto.servicios.repositorys.catalogos.CatalogoGenerosRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de CatalogoGeneroServiceImpl")
class CatalogoGeneroServiceImplTest {

    @Mock
    private CatalogoGenerosRepository catalogoGenerosRepository;

    @Mock
    private CatalogoGeneroMapper catalogoGeneroMapper;

    @InjectMocks
    private CatalogoGeneroServiceImpl servicio;

    @Test
    @DisplayName("obtenerCatalogo devuelve el catalogo mapeado")
    void obtenerCatalogo_devuelveCatalogo() {
        when(catalogoGenerosRepository.findAll()).thenReturn(List.of(genero(1L, "M")));
        when(catalogoGeneroMapper.toResponses(any())).thenReturn(List.of(respuesta(1L, "M")));

        List<CatalogoGeneroResponse> resultado = servicio.obtenerCatalogo();

        assertEquals(1, resultado.size());
        assertEquals("M", resultado.get(0).getTipo());
        verify(catalogoGenerosRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("obtenerPorId devuelve el genero encontrado")
    void obtenerPorId_existente_devuelveGenero() {
        when(catalogoGenerosRepository.findById(1L)).thenReturn(Optional.of(genero(1L, "M")));
        when(catalogoGeneroMapper.toResponse(any())).thenReturn(respuesta(1L, "M"));

        CatalogoGeneroResponse resultado = servicio.obtenerPorId(1L);

        assertEquals(1L, resultado.getId());
        assertEquals("M", resultado.getTipo());
    }

    @Test
    @DisplayName("obtenerPorId inexistente lanza CatalogoNotFoundException")
    void obtenerPorId_inexistente_lanzaNotFound() {
        when(catalogoGenerosRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CatalogoNotFoundException.class, () -> servicio.obtenerPorId(99L));
    }

    @Test
    @DisplayName("crear guarda el genero con el tipo normalizado")
    void crear_exito_guardaTipoNormalizado() {
        when(catalogoGenerosRepository.findByTipo("Femenino")).thenReturn(Optional.empty());
        when(catalogoGeneroMapper.toEntity(any())).thenReturn(genero(null, null));
        when(catalogoGenerosRepository.save(any())).thenAnswer(inv -> {
            CatalogoGeneros entidad = inv.getArgument(0);
            entidad.setId(10L);
            return entidad;
        });
        when(catalogoGeneroMapper.toResponse(any())).thenReturn(respuesta(10L, "Femenino"));

        CatalogoGeneroResponse resultado = servicio.crear(request("  Femenino  "));

        assertEquals(10L, resultado.getId());
        ArgumentCaptor<CatalogoGeneros> captor = ArgumentCaptor.forClass(CatalogoGeneros.class);
        verify(catalogoGenerosRepository, times(1)).save(captor.capture());
        assertEquals("Femenino", captor.getValue().getTipo());
    }

    @Test
    @DisplayName("crear con tipo duplicado lanza excepcion y no guarda")
    void crear_tipoDuplicado_lanzaYNoGuarda() {
        when(catalogoGenerosRepository.findByTipo("M")).thenReturn(Optional.of(genero(1L, "M")));

        assertThrows(IllegalArgumentException.class, () -> servicio.crear(request("M")));

        verify(catalogoGenerosRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear con tipo vacio lanza excepcion")
    void crear_tipoVacio_lanza() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crear(request("   ")));
        verify(catalogoGenerosRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar modifica el genero existente")
    void actualizar_exito_modificaGenero() {
        when(catalogoGenerosRepository.findById(1L)).thenReturn(Optional.of(genero(1L, "M")));
        when(catalogoGenerosRepository.findByTipo("Femenino")).thenReturn(Optional.empty());
        when(catalogoGenerosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogoGeneroMapper.toResponse(any())).thenReturn(respuesta(1L, "Femenino"));

        CatalogoGeneroResponse resultado = servicio.actualizar(1L, request("Femenino"));

        assertEquals("Femenino", resultado.getTipo());
        verify(catalogoGeneroMapper, times(1)).updateEntity(any(), any());
        verify(catalogoGenerosRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("actualizar con el mismo tipo propio no lanza duplicado")
    void actualizar_mismoTipo_noLanzaDuplicado() {
        when(catalogoGenerosRepository.findById(1L)).thenReturn(Optional.of(genero(1L, "M")));
        when(catalogoGenerosRepository.findByTipo("M")).thenReturn(Optional.of(genero(1L, "M")));
        when(catalogoGenerosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogoGeneroMapper.toResponse(any())).thenReturn(respuesta(1L, "M"));

        CatalogoGeneroResponse resultado = servicio.actualizar(1L, request("M"));

        assertEquals("M", resultado.getTipo());
    }

    @Test
    @DisplayName("eliminar borra el genero existente")
    void eliminar_exito_borraGenero() {
        when(catalogoGenerosRepository.findById(1L)).thenReturn(Optional.of(genero(1L, "M")));

        servicio.eliminar(1L);

        verify(catalogoGenerosRepository, times(1)).delete(any(CatalogoGeneros.class));
    }

    @Test
    @DisplayName("eliminar inexistente lanza CatalogoNotFoundException y no borra")
    void eliminar_inexistente_lanzaNotFoundYNoBorra() {
        when(catalogoGenerosRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CatalogoNotFoundException.class, () -> servicio.eliminar(99L));

        verify(catalogoGenerosRepository, never()).delete(any());
    }

    @Test
    @DisplayName("buscarPorTipo sin filtro devuelve todo el catalogo")
    void buscarPorTipo_sinFiltro_devuelveTodo() {
        when(catalogoGenerosRepository.findAll()).thenReturn(List.of(genero(1L, "M")));
        when(catalogoGeneroMapper.toResponses(any())).thenReturn(List.of(respuesta(1L, "M")));

        List<CatalogoGeneroResponse> resultado = servicio.buscarPorTipo("  ");

        assertEquals(1, resultado.size());
        verify(catalogoGenerosRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("buscarPorTipo con filtro delega en el repositorio")
    void buscarPorTipo_conFiltro_delegaEnRepositorio() {
        when(catalogoGenerosRepository.findByTipoContainingIgnoreCaseOrderByTipoAsc("m")
        ).thenReturn(List.of(genero(1L, "Masculino")));
        when(catalogoGeneroMapper.toResponses(any()))
                .thenReturn(List.of(respuesta(1L, "Masculino")));

        List<CatalogoGeneroResponse> resultado = servicio.buscarPorTipo(" m ");

        assertEquals(1, resultado.size());
        verify(catalogoGenerosRepository, times(1))
                .findByTipoContainingIgnoreCaseOrderByTipoAsc("m");
    }

    private static CatalogoGeneros genero(Long id, String tipo) {
        CatalogoGeneros entidad = new CatalogoGeneros();
        entidad.setId(id);
        entidad.setTipo(tipo);
        return entidad;
    }

    private static CatalogoGeneroResponse respuesta(Long id, String tipo) {
        CatalogoGeneroResponse dto = new CatalogoGeneroResponse();
        dto.setId(id);
        dto.setTipo(tipo);
        return dto;
    }

    private static CatalogoGeneroRequest request(String tipo) {
        CatalogoGeneroRequest dto = new CatalogoGeneroRequest();
        dto.setTipo(tipo);
        return dto;
    }
}
