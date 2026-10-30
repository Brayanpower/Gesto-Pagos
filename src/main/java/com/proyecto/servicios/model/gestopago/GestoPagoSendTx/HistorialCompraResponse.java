package com.proyecto.servicios.model.gestopago.GestoPagoSendTx;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistorialCompraResponse {

    private Long id;
    private String upc;
    private String numeroCuenta;
    private Integer idServicio;
    private String nombreServicio;
    private Integer idProducto;
    private String nombreProducto;
    private BigDecimal monto;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoPosterior;
    private String telefono;
    private String referencia;
    private EstatusTransaccion estatus;
    private String folioProveedor;
    private String numAutorizacion;
    private String mensaje;
    private LocalDateTime fechaOperacion;
}

