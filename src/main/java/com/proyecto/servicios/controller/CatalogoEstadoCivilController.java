package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilRequest;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;
import com.proyecto.servicios.service.CatalogoEstadoCivilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/estado-civil")
@Tag(name = "Catálogo de Estado Civil", description = "Endpoints para la gestión del catálogo de estados civiles")
public class CatalogoEstadoCivilController {

    private final CatalogoEstadoCivilService service;

    public CatalogoEstadoCivilController(CatalogoEstadoCivilService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todos los estados civiles", description = "Retorna la lista completa de estados civiles")
    public ResponseEntity<List<CatalogoEstadoCivilResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/buscar", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar estado civil por nombre", description = "Busca estados civiles que coincidan con el término indicado")
    public ResponseEntity<List<CatalogoEstadoCivilResponse>> buscarPorNombre(
            @RequestParam(value = "nombre", required = false) String nombre) {
        return new ResponseEntity<>(service.buscarPorNombre(nombre), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener estado civil por ID", description = "Retorna el detalle de un estado civil por ID")
    public ResponseEntity<CatalogoEstadoCivilResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nuevo estado civil", description = "Registra un nuevo estado civil en el catálogo")
    public ResponseEntity<CatalogoEstadoCivilResponse> crear(@Valid @RequestBody CatalogoEstadoCivilRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar estado civil", description = "Actualiza un estado civil existente")
    public ResponseEntity<CatalogoEstadoCivilResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoEstadoCivilRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar estado civil", description = "Elimina un estado civil por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de estado civil eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
