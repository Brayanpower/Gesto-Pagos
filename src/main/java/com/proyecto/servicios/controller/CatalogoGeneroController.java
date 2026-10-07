package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroRequest;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;
import com.proyecto.servicios.service.CatalogoGeneroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/genero")
@Tag(name = "Catálogo de Género / Sexo", description = "Endpoints para la gestión del catálogo de géneros/sexo")
public class CatalogoGeneroController {

    private final CatalogoGeneroService catalogoGeneroService;

    public CatalogoGeneroController(CatalogoGeneroService catalogoGeneroService) {
        this.catalogoGeneroService = catalogoGeneroService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todos los géneros", description = "Retorna la lista completa de géneros")
    public ResponseEntity<List<CatalogoGeneroResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(catalogoGeneroService.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/buscar", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar género por tipo", description = "Busca géneros por tipo")
    public ResponseEntity<List<CatalogoGeneroResponse>> buscarPorTipo(
            @RequestParam(value = "tipo", required = false) String tipo) {
        return new ResponseEntity<>(catalogoGeneroService.buscarPorTipo(tipo), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener género por ID", description = "Retorna el detalle de un género")
    public ResponseEntity<CatalogoGeneroResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(catalogoGeneroService.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nuevo género", description = "Registra un nuevo género en el catálogo")
    public ResponseEntity<CatalogoGeneroResponse> crear(@Valid @RequestBody CatalogoGeneroRequest request) {
        return new ResponseEntity<>(catalogoGeneroService.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar género", description = "Actualiza un género existente")
    public ResponseEntity<CatalogoGeneroResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoGeneroRequest request) {
        return new ResponseEntity<>(catalogoGeneroService.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar género", description = "Elimina un género por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        catalogoGeneroService.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de genero eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
