package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.banco.UsuarioResponseDTO;
import com.proyecto.servicios.model.seguridad.UsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponseDTO toDTO(Usuario entity);

    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "codigo", ignore = true)
    @Mapping(target = "mensaje", ignore = true)
    UsuarioResponse toResponse(Usuario entity);

    List<UsuarioResponse> toResponses(List<Usuario> entities);
}
