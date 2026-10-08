package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadRequest;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;
import com.proyecto.servicios.service.CatalogoNacionalidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/nacionalidad")
@Tag(name = "Catálogo de Nacionalidad", description = "Endpoints para la gestión del catálogo de nacionalidades")
public class CatalogoNacionalidadController {

    private final CatalogoNacionalidadService service;

    public CatalogoNacionalidadController(CatalogoNacionalidadService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todas las nacionalidades", description = "Retorna la lista completa de nacionalidades registradas")
    public ResponseEntity<List<CatalogoNacionalidadResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/buscar", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar nacionalidad por nombre", description = "Busca nacionalidades que coincidan parcialmente con el nombre")
    public ResponseEntity<List<CatalogoNacionalidadResponse>> buscarPorNombre(
            @RequestParam(value = "nombre", required = false) String nombre) {
        return new ResponseEntity<>(service.buscarPorNombre(nombre), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener nacionalidad por ID", description = "Retorna el detalle de una nacionalidad por su ID")
    public ResponseEntity<CatalogoNacionalidadResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nueva nacionalidad", description = "Registra una nueva nacionalidad en el catálogo")
    public ResponseEntity<CatalogoNacionalidadResponse> crear(@Valid @RequestBody CatalogoNacionalidadRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar nacionalidad", description = "Actualiza una nacionalidad existente")
    public ResponseEntity<CatalogoNacionalidadResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoNacionalidadRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar nacionalidad", description = "Elimina una nacionalidad por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de nacionalidad eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
