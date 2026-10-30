package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.HistorialCompraResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.PagoServicioResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;
import com.proyecto.servicios.service.PagoServicioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/servicios")
@Tag(
        name = "Pagos de Servicios y Recargas (GestoPago)",
        description = "Operaciones de recargas y pago de servicios con cargo a cuenta bancaria, conciliación e historial paginado del cliente"
)
@SecurityRequirement(name = "BearerAuth")
public class PagoServicioController {

    private final PagoServicioService pagoServicioService;

    public PagoServicioController(PagoServicioService pagoServicioService) {
        this.pagoServicioService = pagoServicioService;
    }

    @PostMapping(
            value = "/pagos",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Realizar recarga o pago de servicio",
            description = "Valida identidad y autorización del cliente, existencia y estatus activo de la cuenta, saldo suficiente, "
                    + "catálogo y precio del producto, idempotencia y ejecuta el cargo e integración con GestoPago sendTx.do"
    )
    public ResponseEntity<PagoServicioResponse> realizarPagoServicio(
            @Valid @RequestBody SendTxRequest request,
            @Parameter(description = "Clave única de idempotencia para evitar cargos duplicados")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        PagoServicioResponse response = pagoServicioService.procesarPagoServicio(request, idempotencyKey);
        HttpStatus status = Boolean.TRUE.equals(response.getRespuestaIdempotente())
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return new ResponseEntity<>(response, status);
    }

    @GetMapping(value = "/historial", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consultar historial de compras del cliente (paginado)",
            description = "Retorna el historial paginado de recargas y pagos de servicios del cliente autenticado, "
                    + "de forma general o filtrado por rango de fechas, número de cuenta y estatus"
    )
    public ResponseEntity<PageResponse<HistorialCompraResponse>> obtenerHistorialCompras(
            @Parameter(description = "Fecha inicial del filtro en formato yyyy-MM-dd", example = "2026-10-01")
            @RequestParam(value = "fechaInicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,

            @Parameter(description = "Fecha final del filtro en formato yyyy-MM-dd (inclusiva)", example = "2026-10-31")
            @RequestParam(value = "fechaFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,

            @Parameter(description = "Número de cuenta del cliente (opcional)", example = "1234567890")
            @RequestParam(value = "numeroCuenta", required = false) String numeroCuenta,

            @Parameter(description = "Filtrar por estatus de la transacción (opcional)")
            @RequestParam(value = "estatus", required = false) EstatusTransaccion estatus,

            @Parameter(description = "Número de página (inicia en 0)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Cantidad de registros por página (1 a 100)", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,

            @Parameter(description = "Campo de ordenamiento (fechaCreacion, monto, estatus, idServicio)", example = "fechaCreacion")
            @RequestParam(value = "sortBy", defaultValue = "fechaCreacion") String sortBy,

            @Parameter(description = "Dirección de ordenamiento (ASC o DESC)", example = "DESC")
            @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir) {

        PageResponse<HistorialCompraResponse> historial = pagoServicioService.obtenerHistorialCompras(
                fechaInicio, fechaFin, numeroCuenta, estatus, page, size, sortBy, sortDir);
        return new ResponseEntity<>(historial, HttpStatus.OK);
    }

    @GetMapping(value = "/pagos/{upc}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Consultar estatus de transacción por folio UPC",
            description = "Permite al cliente verificar el estatus actual de una compra o recarga mediante su folio UPC"
    )
    public ResponseEntity<PagoServicioResponse> consultarPorUpc(@PathVariable("upc") String upc) {
        return new ResponseEntity<>(pagoServicioService.consultarTransaccionPorUpc(upc), HttpStatus.OK);
    }

    @PostMapping(value = "/pagos/{upc}/conciliar", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Conciliar transacción pendiente con el proveedor",
            description = "Consulta en GestoPago el resultado real de una operación en estatus PENDIENTE_CONCILIACION "
                    + "sin reintentar el cargo, confirmando el éxito o restituyendo el saldo si no fue procesada"
    )
    public ResponseEntity<PagoServicioResponse> conciliarPorUpc(@PathVariable("upc") String upc) {
        return new ResponseEntity<>(pagoServicioService.conciliarTransaccion(upc), HttpStatus.OK);
    }
}

