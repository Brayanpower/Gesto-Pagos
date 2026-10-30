package com.proyecto.servicios.exception;

import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import lombok.Getter;

@Getter
public class TransaccionTimeoutConciliacionException extends RuntimeException {

    private final String upc;
    private final EstatusTransaccion estatus;

    public TransaccionTimeoutConciliacionException(String upc, String message) {
        super(message);
        this.upc = upc;
        this.estatus = EstatusTransaccion.PENDIENTE_CONCILIACION;
    }

    public TransaccionTimeoutConciliacionException(String upc, String message, Throwable cause) {
        super(message, cause);
        this.upc = upc;
        this.estatus = EstatusTransaccion.PENDIENTE_CONCILIACION;
    }
}

