package com.proyecto.servicios.model.gestopago.GestoPagoSendTx;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;

import java.math.BigDecimal;

public record ReservaSaldoCommand(
        Long clienteAutenticadoId,
        String numeroCuenta,
        String claveIdempotencia,
        CatalogoProducto producto,
        Integer idServicio,
        Integer idProducto,
        BigDecimal monto,
        String telefono,
        String referencia
) {
}

