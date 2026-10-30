package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import com.proyecto.servicios.model.catalogos.CatalogoPaisRequest;
import com.proyecto.servicios.model.catalogos.CatalogoPaisResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoPaisMapper {

    @Mapping(target = "id", ignore = true)
    CatalogoPais toEntity(CatalogoPaisRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntity(CatalogoPaisRequest request, @MappingTarget CatalogoPais entity);

    CatalogoPaisResponse toResponse(CatalogoPais entity);

    List<CatalogoPaisResponse> toResponses(List<CatalogoPais> entities);
}
