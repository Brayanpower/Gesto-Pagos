package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoColonia;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaRequest;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoColoniaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoMunicipio", ignore = true)
    CatalogoColonia toEntity(CatalogoColoniaRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoMunicipio", ignore = true)
    void updateEntity(CatalogoColoniaRequest request, @MappingTarget CatalogoColonia entity);

    @Mapping(target = "municipioId", source = "catalogoMunicipio.id")
    @Mapping(target = "municipioNombre", source = "catalogoMunicipio.municipio")
    CatalogoColoniaResponse toResponse(CatalogoColonia entity);

    List<CatalogoColoniaResponse> toResponses(List<CatalogoColonia> entities);
}
