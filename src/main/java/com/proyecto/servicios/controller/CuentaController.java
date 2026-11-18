package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cuentas")
@Tag(name = "Cuentas de Ahorro & Revisión de Onboarding", description = "Consulta de cuenta única de ahorro del cliente y revisión cuenta por cuenta de onboarding exclusiva para ROLE_ADMIN")
@SecurityRequirement(name = "BearerAuth")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping(value = "/mi-cuenta", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Consultar mi cuenta de ahorro", description = "Retorna la única cuenta de ahorro asignada en el onboarding al cliente autenticado")
    public ResponseEntity<CuentaResponse> obtenerMiCuenta() {
        return new ResponseEntity<>(cuentaService.obtenerMiCuenta(), HttpStatus.OK);
    }

    @GetMapping(value = "/mi-cuenta/saldo", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Consultar saldo de mi cuenta de ahorro", description = "Retorna el número de cuenta, tipo, estatus y saldo disponible de la cuenta de ahorro del cliente autenticado")
    public ResponseEntity<Map<String, Object>> consultarMiSaldo() {
        CuentaResponse cuenta = cuentaService.obtenerMiCuenta();
        Map<String, Object> response = new HashMap<>();
        response.put("numeroCuenta", cuenta.getNumeroCuenta());
        response.put("tipoCuenta", cuenta.getTipoCuenta());
        response.put("estatus", cuenta.getEstatus());
        response.put("saldo", cuenta.getSaldo());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping(value = "/pendientes", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar cuentas de onboarding pendientes de revisión (Solo ADMIN)", description = "Devuelve las cuentas de ahorro creadas en onboarding que esperan aprobación o rechazo por un administrador")
    public ResponseEntity<List<CuentaResponse>> obtenerPendientesRevision() {
        return new ResponseEntity<>(cuentaService.obtenerPendientesRevision(), HttpStatus.OK);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar cuentas con filtros (Solo ADMIN)", description = "Lista todas las cuentas de ahorro del sistema con posibilidad de filtrar por clienteId o estatus")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    @Operation(summary = "Consultar cuenta por número", description = "ADMIN puede revisar cualquier cuenta; CLIENTE solo su propia cuenta de ahorro")
    public ResponseEntity<CuentaResponse> obtenerPorNumeroCuenta(@PathVariable("numeroCuenta") String numeroCuenta) {
        return new ResponseEntity<>(cuentaService.obtenerPorNumeroCuenta(numeroCuenta), HttpStatus.OK);
    }

    @GetMapping(value = "/{numeroCuenta}/saldo", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    @Operation(summary = "Consultar saldo de cuenta", description = "ADMIN puede revisar cualquier saldo; CLIENTE solo el de su propia cuenta de ahorro")
    public ResponseEntity<Map<String, Object>> consultarSaldo(@PathVariable("numeroCuenta") String numeroCuenta) {
        BigDecimal saldo = cuentaService.consultarSaldo(numeroCuenta);
        Map<String, Object> response = new HashMap<>();
        response.put("numeroCuenta", numeroCuenta);
        response.put("saldo", saldo);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping(value = "/{numeroCuenta}/revision", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revisar y dictaminar cuenta de onboarding (Solo ADMIN)", description = "Permite al administrador revisar una cuenta de onboarding y aprobarla (ACTIVA), rechazarla (RECHAZADA) o cambiar su estatus operativo")
    public ResponseEntity<CuentaResponse> revisarCuenta(
            @PathVariable("numeroCuenta") String numeroCuenta,
            @Valid @RequestBody CuentaActualizacionRequest request) {
        return new ResponseEntity<>(cuentaService.revisarCuenta(numeroCuenta, request), HttpStatus.OK);
    }

    @PutMapping(value = "/{numeroCuenta}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar estatus de cuenta (Solo ADMIN)", description = "Modifica el estatus operativo de una cuenta bancaria durante la revisión o administración")
    public ResponseEntity<CuentaResponse> actualizarEstatus(
            @PathVariable("numeroCuenta") String numeroCuenta,
            @Valid @RequestBody CuentaActualizacionRequest request) {
        return new ResponseEntity<>(cuentaService.revisarCuenta(numeroCuenta, request), HttpStatus.OK);
    }
}
