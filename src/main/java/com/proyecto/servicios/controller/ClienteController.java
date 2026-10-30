package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.banco.ClienteRegistroRequest;
import com.proyecto.servicios.model.banco.ClienteResponse;
import com.proyecto.servicios.model.banco.ClienteUpdateRequest;
import com.proyecto.servicios.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Gestión de Clientes", description = "Endpoints para el registro integral, consulta, actualización y baja lógica de clientes bancarios")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar Cliente Bancario", description = "Ejecuta el flujo transaccional de alta: Cliente -> Domicilio -> Cuenta Bancaria Inicial -> Usuario con contraseña BCrypt")
    public ResponseEntity<ClienteResponse> registrarCliente(@Valid @RequestBody ClienteRegistroRequest request) {
        return new ResponseEntity<>(clienteService.registrarCliente(request), HttpStatus.CREATED);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar clientes con filtros", description = "Permite filtrar clientes activos por nombre, apellido paterno, apellido materno, CURP, RFC o correo electrónico")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<List<ClienteResponse>> buscarClientes(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "apellidoPaterno", required = false) String apellidoPaterno,
            @RequestParam(value = "apellidoMaterno", required = false) String apellidoMaterno,
            @RequestParam(value = "curp", required = false) String curp,
            @RequestParam(value = "rfc", required = false) String rfc,
            @RequestParam(value = "correo", required = false) String correo) {
        return new ResponseEntity<>(clienteService.buscarConFiltros(nombre, apellidoPaterno, apellidoMaterno, curp, rfc, correo), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar cliente por ID", description = "Obtiene los datos completos de un cliente activo, incluyendo su domicilio, cuentas y usuario")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ClienteResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(clienteService.obtenerPorId(id), HttpStatus.OK);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar cliente", description = "Actualiza los campos permitidos del cliente (datos personales, contacto, domicilio, laboral). Campos sensibles como CURP y RFC no son modificables.")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ClienteResponse> actualizarCliente(
            @PathVariable("id") Long id,
            @Valid @RequestBody ClienteUpdateRequest request) {
        return new ResponseEntity<>(clienteService.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Baja lógica de cliente", description = "Inactiva al cliente, desactiva su usuario de acceso y marca sus cuentas bancarias activas como inactivas")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<GenericResponse> darDeBajaCliente(@PathVariable("id") Long id) {
        clienteService.darDeBaja(id);
        GenericResponse response = new GenericResponse();
        response.setCodigo(0);
        response.setMensaje("Cliente, usuario y cuentas inactivados correctamente");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
