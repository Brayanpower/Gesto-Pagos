package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaRequest;
import com.proyecto.servicios.model.catalogos.CatalogoColoniaResponse;
import com.proyecto.servicios.service.CatalogoColoniaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/colonia")
@Tag(name = "Catálogo de Colonia", description = "Endpoints para la gestión del catálogo de colonias")
public class CatalogoColoniaController {

    private final CatalogoColoniaService service;

    public CatalogoColoniaController(CatalogoColoniaService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todas las colonias", description = "Retorna la lista de todas las colonias")
    public ResponseEntity<List<CatalogoColoniaResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/municipio/{municipioId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar colonias por ID de municipio", description = "Retorna la lista de colonias pertenecientes a un municipio")
    public ResponseEntity<List<CatalogoColoniaResponse>> buscarPorMunicipio(@PathVariable("municipioId") Long municipioId) {
        return new ResponseEntity<>(service.buscarPorMunicipio(municipioId), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener colonia por ID", description = "Retorna el detalle de una colonia")
    public ResponseEntity<CatalogoColoniaResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nueva colonia", description = "Registra una nueva colonia asociada a un municipio")
    public ResponseEntity<CatalogoColoniaResponse> crear(@Valid @RequestBody CatalogoColoniaRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar colonia", description = "Actualiza una colonia existente")
    public ResponseEntity<CatalogoColoniaResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoColoniaRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar colonia", description = "Elimina una colonia por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de colonia eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
