package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.clientes.DomicilioCliente;
import com.proyecto.servicios.model.banco.DomicilioDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface DomicilioClienteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cliente", ignore = true)
    @Mapping(target = "colonia", ignore = true)
    @Mapping(target = "municipio", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "pais", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    DomicilioCliente toEntity(DomicilioDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cliente", ignore = true)
    @Mapping(target = "colonia", ignore = true)
    @Mapping(target = "municipio", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target = "pais", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    void updateEntity(DomicilioDTO dto, @MappingTarget DomicilioCliente entity);

    @Mapping(target = "coloniaId", source = "colonia.id")
    @Mapping(target = "coloniaNombre", source = "colonia.colonia")
    @Mapping(target = "municipioId", source = "municipio.id")
    @Mapping(target = "municipioNombre", source = "municipio.municipio")
    @Mapping(target = "estadoId", source = "estado.id")
    @Mapping(target = "estadoNombre", source = "estado.estado")
    @Mapping(target = "paisId", source = "pais.id")
    @Mapping(target = "paisNombre", source = "pais.nombre")
    DomicilioDTO toDTO(DomicilioCliente entity);
}
