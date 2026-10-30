package com.proyecto.servicios.model.gestopago.GestoPagoSendTx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GestoPagoTxResult {

    public enum EstadoProveedor {
        APROBADA,
        RECHAZADA_PROVEEDOR,
        PENDIENTE_PROVEEDOR
    }

    private EstadoProveedor estado;
    private String codigo;
    private String mensaje;
    private String idTx;
    private String numAutorizacion;
}

