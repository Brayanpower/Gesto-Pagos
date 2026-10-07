package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoPaisRequest;
import com.proyecto.servicios.model.catalogos.CatalogoPaisResponse;
import com.proyecto.servicios.service.CatalogoPaisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/pais")
@Tag(name = "Catálogo de País", description = "Endpoints para la gestión del catálogo de países")
public class CatalogoPaisController {

    private final CatalogoPaisService service;

    public CatalogoPaisController(CatalogoPaisService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todos los países", description = "Retorna la lista completa de países")
    public ResponseEntity<List<CatalogoPaisResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/buscar", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar país por nombre", description = "Busca países por nombre")
    public ResponseEntity<List<CatalogoPaisResponse>> buscarPorNombre(
            @RequestParam(value = "nombre", required = false) String nombre) {
        return new ResponseEntity<>(service.buscarPorNombre(nombre), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener país por ID", description = "Retorna el detalle de un país")
    public ResponseEntity<CatalogoPaisResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nuevo país", description = "Registra un nuevo país en el catálogo")
    public ResponseEntity<CatalogoPaisResponse> crear(@Valid @RequestBody CatalogoPaisRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar país", description = "Actualiza un país existente")
    public ResponseEntity<CatalogoPaisResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoPaisRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar país", description = "Elimina un país por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de pais eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
