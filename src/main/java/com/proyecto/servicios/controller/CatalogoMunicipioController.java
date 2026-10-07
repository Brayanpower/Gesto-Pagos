package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioRequest;
import com.proyecto.servicios.model.catalogos.CatalogoMunicipioResponse;
import com.proyecto.servicios.service.CatalogoMunicipioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos/municipio")
@Tag(name = "Catálogo de Municipio", description = "Endpoints para la gestión del catálogo de municipios")
public class CatalogoMunicipioController {

    private final CatalogoMunicipioService service;

    public CatalogoMunicipioController(CatalogoMunicipioService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener todos los municipios", description = "Retorna la lista de todos los municipios")
    public ResponseEntity<List<CatalogoMunicipioResponse>> obtenerCatalogo() {
        return new ResponseEntity<>(service.obtenerCatalogo(), HttpStatus.OK);
    }

    @GetMapping(value = "/estado/{estadoId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar municipios por ID de estado", description = "Retorna la lista de municipios pertenecientes a un estado")
    public ResponseEntity<List<CatalogoMunicipioResponse>> buscarPorEstado(@PathVariable("estadoId") Long estadoId) {
        return new ResponseEntity<>(service.buscarPorEstado(estadoId), HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener municipio por ID", description = "Retorna el detalle de un municipio")
    public ResponseEntity<CatalogoMunicipioResponse> obtenerPorId(@PathVariable("id") Long id) {
        return new ResponseEntity<>(service.obtenerPorId(id), HttpStatus.OK);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nuevo municipio", description = "Registra un nuevo municipio asociado a un estado")
    public ResponseEntity<CatalogoMunicipioResponse> crear(@Valid @RequestBody CatalogoMunicipioRequest request) {
        return new ResponseEntity<>(service.crear(request), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar municipio", description = "Actualiza un municipio existente")
    public ResponseEntity<CatalogoMunicipioResponse> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogoMunicipioRequest request) {
        return new ResponseEntity<>(service.actualizar(id, request), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Eliminar municipio", description = "Elimina un municipio por su ID")
    public ResponseEntity<GenericResponse> eliminar(@PathVariable("id") Long id) {
        service.eliminar(id);
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(0);
        respuesta.setMensaje("Catalogo de municipio eliminado correctamente");
        return new ResponseEntity<>(respuesta, HttpStatus.OK);
    }
}
