package com.proyecto.servicios.model.notificacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos inmutables para construir el comprobante electrónico de un pago de servicio exitoso.
 */
public record ComprobantePagoEmailData(
        String upc,
        String numeroCuenta,
        String nombreServicio,
        String nombreProducto,
        BigDecimal monto,
        BigDecimal saldoDisponible,
        String telefono,
        String referencia,
        String folioProveedor,
        String numAutorizacion,
        LocalDateTime fechaOperacion) {
}
