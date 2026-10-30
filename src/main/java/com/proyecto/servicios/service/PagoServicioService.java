package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.HistorialCompraResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.PagoServicioResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;

import java.time.LocalDate;

public interface PagoServicioService {

    PagoServicioResponse procesarPagoServicio(SendTxRequest request, String headerIdempotencyKey);

    PagoServicioResponse consultarTransaccionPorUpc(String upc);

    PagoServicioResponse conciliarTransaccion(String upc);

    PageResponse<HistorialCompraResponse> obtenerHistorialCompras(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String numeroCuenta,
            EstatusTransaccion estatus,
            int page,
            int size,
            String sortBy,
            String sortDir);
}

