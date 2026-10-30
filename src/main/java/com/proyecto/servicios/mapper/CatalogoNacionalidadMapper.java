package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoNacionalidad;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadRequest;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoNacionalidadMapper {

    @Mapping(target = "id", ignore = true)
    CatalogoNacionalidad toEntity(CatalogoNacionalidadRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntity(CatalogoNacionalidadRequest request, @MappingTarget CatalogoNacionalidad entity);

    CatalogoNacionalidadResponse toResponse(CatalogoNacionalidad entity);

    List<CatalogoNacionalidadResponse> toResponses(List<CatalogoNacionalidad> entities);
}
