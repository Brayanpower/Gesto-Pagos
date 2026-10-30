package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.model.banco.ClienteRegistroRequest;
import com.proyecto.servicios.model.banco.ClienteResponse;
import com.proyecto.servicios.model.banco.ClienteUpdateRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", uses = {
        CatalogoGeneroMapper.class,
        CatalogoNacionalidadMapper.class,
        CatalogoEstadoCivilMapper.class,
        DomicilioClienteMapper.class,
        CuentaMapper.class,
        UsuarioMapper.class
})
public interface ClienteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "genero", ignore = true)
    @Mapping(target = "nacionalidad", ignore = true)
    @Mapping(target = "estadoCivil", ignore = true)
    @Mapping(target = "domicilio", ignore = true)
    @Mapping(target = "cuentas", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    Cliente toEntity(ClienteRegistroRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "curp", ignore = true)
    @Mapping(target = "rfc", ignore = true)
    @Mapping(target = "genero", ignore = true)
    @Mapping(target = "nacionalidad", ignore = true)
    @Mapping(target = "estadoCivil", ignore = true)
    @Mapping(target = "domicilio", ignore = true)
    @Mapping(target = "cuentas", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    void updateEntity(ClienteUpdateRequest request, @MappingTarget Cliente entity);

    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "mensaje", ignore = true)
    ClienteResponse toResponse(Cliente entity);

    List<ClienteResponse> toResponses(List<Cliente> entities);
}
