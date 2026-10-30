package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoResponse;
import com.proyecto.servicios.service.CatalogoEstadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/estado")
@Tag(name = "Catálogo de Estado", description = "Endpoints para la gestión del catálogo de estados/entidades federativas")
public class CatalogoEstadoController {

    private final CatalogoEstadoService service;

    public CatalogoEstadoController(CatalogoEstadoService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todos los estados", description = "Retorna la lista de todos los estados")
    public ResponseEntity<List<CatalogoEstadoResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/pais/{paisId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar estados por ID de país", description = "Retorna la lista de estados pertenecientes a un país")
    public ResponseEntity<List<CatalogoEstadoResponse>> buscarPorPais(@PathVariable("paisId") Long paisId) {
        return new ResponseEntity<>(service.buscarPorPais(paisId), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener estado por ID", description = "Retorna el detalle de un estado")
    public ResponseEntity<CatalogoEstadoResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nuevo estado", description = "Registra un nuevo estado asociado a un país")
    public ResponseEntity<CatalogoEstadoResponse> crear(@Valid @RequestBody CatalogoEstadoRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar estado", description = "Actualiza un estado existente")
    public ResponseEntity<CatalogoEstadoResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoEstadoRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar estado", description = "Elimina un estado por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de estado eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
