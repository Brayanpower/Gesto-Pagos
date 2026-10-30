package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.model.banco.CuentaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CuentaMapper {

    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", expression = "java(entity.getCliente() != null ? entity.getCliente().getNombre() + \" \" + entity.getCliente().getApellidoPaterno() : null)")
    CuentaResponse toResponse(Cuenta entity);

    List<CuentaResponse> toResponses(List<Cuenta> entities);
}
