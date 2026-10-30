package com.proyecto.servicios.service;

import com.proyecto.servicios.model.seguridad.UsuarioActualizacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaResponse;
import com.proyecto.servicios.model.seguridad.UsuarioCreacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioEstadoRequest;
import com.proyecto.servicios.model.seguridad.UsuarioResponse;

public interface UsuarioService {

    UsuarioResponse crear(UsuarioCreacionRequest request);

    UsuarioBusquedaResponse buscar(UsuarioBusquedaRequest request);

    UsuarioResponse actualizar(UsuarioActualizacionRequest request);

    UsuarioResponse cambiarEstado(UsuarioEstadoRequest request);
}
