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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Gestión de Clientes (Onboarding)", description = "Endpoints para onboarding público de clientes, consulta de perfil propio y revisión/administración por rol ADMIN")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Onboarding de Cliente Bancario", description = "Registro público: crea Cliente, Domicilio, su única Cuenta de Ahorro (en estatus PENDIENTE_REVISION) y Usuario ROLE_CLIENTE")
    public ResponseEntity<ClienteResponse> registrarCliente(@Valid @RequestBody ClienteRegistroRequest request) {
        return new ResponseEntity<>(clienteService.registrarCliente(request), HttpStatus.CREATED);
    }

    @GetMapping(value = "/mi-perfil", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Consultar mi perfil y mi cuenta de ahorro", description = "Retorna la información completa del cliente autenticado y su única cuenta de ahorro")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ClienteResponse> obtenerMiPerfil() {
        return new ResponseEntity<>(clienteService.obtenerMiPerfil(), HttpStatus.OK);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar clientes con filtros (Solo ADMIN)", description = "Permite al administrador filtrar clientes activos por nombre, apellidos, CURP, RFC o correo para revisión de onboarding")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    @Operation(summary = "Consultar cliente por ID", description = "Obtiene el expediente de un cliente activo (ADMIN puede revisar cualquiera; CLIENTE solo su propio ID)")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ClienteResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(clienteService.obtenerPorId(id), HttpStatus.OK);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    @Operation(summary = "Actualizar cliente", description = "Actualiza datos permitidos del cliente (ADMIN o el propio cliente titular)")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ClienteResponse> actualizarCliente(
            @PathVariable("id") Long id,
            @Valid @RequestBody ClienteUpdateRequest request) {
        return new ResponseEntity<>(clienteService.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Baja lógica de cliente (Solo ADMIN)", description = "Inactiva al cliente, desactiva su usuario de acceso y su cuenta de ahorro")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<GenericResponse> darDeBajaCliente(@PathVariable("id") Long id) {
        clienteService.darDeBaja(id);
        GenericResponse response = new GenericResponse();
        response.setCodigo(0);
        response.setMensaje("Cliente, usuario y cuenta de ahorro inactivados correctamente");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
