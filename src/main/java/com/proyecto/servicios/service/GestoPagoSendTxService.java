package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;

public interface GestoPagoSendTxService {

    String sendTx(SendTx request);

    GestoPagoTxResult ejecutarTransaccion(SendTx request);

    GestoPagoTxResult verificarTransaccion(SendTx request);
}
