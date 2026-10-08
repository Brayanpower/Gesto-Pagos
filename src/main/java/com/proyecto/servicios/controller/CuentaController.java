package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaCreacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cuentas")
@Tag(name = "Gestión de Cuentas Bancarias", description = "Endpoints para la apertura, consulta de saldo, filtrado y administración de cuentas bancarias")
@SecurityRequirement(name = "BearerAuth")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear nueva cuenta bancaria", description = "Abre una nueva cuenta bancaria para un cliente activo existente con saldo inicial")
    public ResponseEntity<CuentaResponse> crearCuenta(@Valid @RequestBody CuentaCreacionRequest request) {
        return new ResponseEntity<>(cuentaService.crearCuenta(request), HttpStatus.CREATED);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar cuentas con filtros", description = "Lista cuentas bancarias con posibilidad de filtrar por clienteId o estatus (ACTIVA, INACTIVA, BLOQUEADA, CANCELADA)")
    public ResponseEntity<List<CuentaResponse>> obtenerCuentas(
            @RequestParam(value = "clienteId", required = false) Long clienteId,
            @RequestParam(value = "estatus", required = false) EstatusCuenta estatus) {

        if (clienteId != null) {
            return new ResponseEntity<>(cuentaService.obtenerPorClienteId(clienteId), HttpStatus.OK);
        }
        if (estatus != null) {
            return new ResponseEntity<>(cuentaService.obtenerPorEstatus(estatus), HttpStatus.OK);
        }
        return new ResponseEntity<>(cuentaService.listarTodas(), HttpStatus.OK);
    }

    @GetMapping(value = "/{numeroCuenta}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar cuenta por número", description = "Obtiene los detalles de una cuenta bancaria específica a partir de su número único")
    public ResponseEntity<CuentaResponse> obtenerPorNumeroCuenta(@PathVariable("numeroCuenta") String numeroCuenta) {
        return new ResponseEntity<>(cuentaService.obtenerPorNumeroCuenta(numeroCuenta), HttpStatus.OK);
    }

    @GetMapping(value = "/{numeroCuenta}/saldo", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar saldo de cuenta", description = "Retorna el saldo disponible actual de la cuenta bancaria")
    public ResponseEntity<Map<String, Object>> consultarSaldo(@PathVariable("numeroCuenta") String numeroCuenta) {
        BigDecimal saldo = cuentaService.consultarSaldo(numeroCuenta);
        Map<String, Object> response = new HashMap<>();
        response.put("numeroCuenta", numeroCuenta);
        response.put("saldo", saldo);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping(value = "/{numeroCuenta}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar estatus de cuenta", description = "Modifica el estatus operativo de una cuenta bancaria")
    public ResponseEntity<CuentaResponse> actualizarEstatus(
            @PathVariable("numeroCuenta") String numeroCuenta,
            @Valid @RequestBody CuentaActualizacionRequest request) {
        return new ResponseEntity<>(cuentaService.actualizarEstatus(numeroCuenta, request), HttpStatus.OK);
    }
}
