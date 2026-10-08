package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoEstadoCivilMapper {

    @Mapping(target = "id", ignore = true)
    CatalogoEstadoCivil toEntity(CatalogoEstadoCivilRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntity(CatalogoEstadoCivilRequest request, @MappingTarget CatalogoEstadoCivil entity);

    CatalogoEstadoCivilResponse toResponse(CatalogoEstadoCivil entity);

    List<CatalogoEstadoCivilResponse> toResponses(List<CatalogoEstadoCivil> entities);
}
