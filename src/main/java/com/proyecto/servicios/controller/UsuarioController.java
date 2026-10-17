package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.seguridad.UsuarioActualizacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaResponse;
import com.proyecto.servicios.model.seguridad.UsuarioCreacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioEstadoRequest;
import com.proyecto.servicios.model.seguridad.UsuarioResponse;
import com.proyecto.servicios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Administración de Usuarios", description = "Endpoints para alta, consulta con filtros combinables, actualización y activación/desactivación de usuarios. Todos los datos se reciben por body (sin PathVariable ni RequestParam)")
@SecurityRequirement(name = "BearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear usuario de acceso", description = "Registra un usuario vinculado a un cliente existente con contraseña BCrypt. El id, el estado y las fechas los asigna el servidor")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioCreacionRequest request) {
        return new ResponseEntity<>(usuarioService.crear(request), HttpStatus.CREATED);
    }

    @PostMapping(value = "/buscar", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar usuarios con filtros combinables", description = "Único endpoint de consulta: combina opcionalmente id, correo, rol, activo y clienteId")
    public ResponseEntity<UsuarioBusquedaResponse> buscar(@Valid @RequestBody UsuarioBusquedaRequest request) {
        return new ResponseEntity<>(usuarioService.buscar(request), HttpStatus.OK);
    }

    @PutMapping(value = "/actualizar", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar usuario", description = "Actualiza correo, contraseña y/o rol. No permite modificar id, fechas de creación ni password hash")
    public ResponseEntity<UsuarioResponse> actualizar(@Valid @RequestBody UsuarioActualizacionRequest request) {
        return new ResponseEntity<>(usuarioService.actualizar(request), HttpStatus.OK);
    }

    @PutMapping(value = "/estado", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Activar o desactivar usuario", description = "Cambia el estado lógico del usuario a partir de su id enviado en el body")
    public ResponseEntity<UsuarioResponse> cambiarEstado(@Valid @RequestBody UsuarioEstadoRequest request) {
        return new ResponseEntity<>(usuarioService.cambiarEstado(request), HttpStatus.OK);
    }
}
