package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.banco.UsuarioResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponseDTO toDTO(Usuario entity);
}
