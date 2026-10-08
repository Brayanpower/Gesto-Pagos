package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroRequest;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoGeneroMapper {

    @Mapping(target = "id", ignore = true)
    CatalogoGeneros toEntity(CatalogoGeneroRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntity(CatalogoGeneroRequest request, @MappingTarget CatalogoGeneros entity);

    CatalogoGeneroResponse toResponse(CatalogoGeneros entity);

    List<CatalogoGeneroResponse> toResponses(List<CatalogoGeneros> entities);
}
