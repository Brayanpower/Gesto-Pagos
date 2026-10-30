package com.proyecto.servicios.model.gestopago.GestoPagoSendTx;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendTx {

    @NotNull(message = "Tu idProducto es obligatorio")
    private Integer idProducto;

    @NotNull(message = "Tu idServicio es obligatorio")
    private Integer idServicio;

    @NotBlank(message = "El telefono es obligatorio")
    @Size(min = 10, max = 20, message = "El telefono debe tener entre 10 y 20 caracteres")
    private String telefono;

    @NotBlank(message = "La hora local es obligatoria")
    @Pattern(
            regexp = "^(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)$",
            message = "El formato debe ser yyyyMMddHHmmss y representar una fecha valida"
    )
    private String horaLocal;

    @Size(max = 100, message = "La referencia debe tener maximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]*$", message = "La referencia solo debe contener letras, numeros y guiones")
    private String referencia;

    @NotNull(message = "El campo Monto no debe ir vacio")
    private BigDecimal montoPago;

    @Size(min = 3, max = 15, message = "Tu upc debe tener una longitud de 3 a 15")
    private String upc;

    @NotBlank(message = "Tu unidad es obligatoria")
    @Size(min = 1, max = 25, message = "Tu unidad debe tener una longitud de 1 a 25")
    private String unidad;
}
