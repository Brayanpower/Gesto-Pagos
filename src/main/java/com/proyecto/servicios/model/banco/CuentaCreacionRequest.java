package com.proyecto.servicios.model.banco;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuentaCreacionRequest {

    @NotNull(message = "El id del cliente es obligatorio")
    private Long clienteId;

    @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo")
    private BigDecimal saldoInicial;
}
