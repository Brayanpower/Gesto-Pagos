package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioRequest;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogoMunicipioMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoEstado", ignore = true)
    CatalogoMunicipio toEntity(CatalogoMunicipioRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "catalogoEstado", ignore = true)
    void updateEntity(CatalogoMunicipioRequest request, @MappingTarget CatalogoMunicipio entity);

    @Mapping(target = "estadoId", source = "catalogoEstado.id")
    @Mapping(target = "estadoNombre", source = "catalogoEstado.estado")
    CatalogoMunicipioResponse toResponse(CatalogoMunicipio entity);

    List<CatalogoMunicipioResponse> toResponses(List<CatalogoMunicipio> entities);
}
