package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoEstadoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoPais", ignore = true)
    CatalogoEstado toEntity(CatalogoEstadoRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoPais", ignore = true)
    void updateEntity(CatalogoEstadoRequest request, @MappingTarget CatalogoEstado entity);

    @Mapping(target = "paisId", source = "catalogoPais.id")
    @Mapping(target = "paisNombre", source = "catalogoPais.nombre")
    CatalogoEstadoResponse toResponse(CatalogoEstado entity);

    List<CatalogoEstadoResponse> toResponses(List<CatalogoEstado> entities);
}
