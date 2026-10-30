package com.proyecto.servicios.model.gestopago.GestoPagoSendTx;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Solicitud de recarga o pago de servicio con cargo a cuenta bancaria del cliente")
public class SendTxRequest {

    @NotBlank(message = "El numero de cuenta es obligatorio")
    @Size(min = 10, max = 20, message = "El numero de cuenta debe tener entre 10 y 20 digitos")
    @Pattern(regexp = "^[0-9]{10,20}$", message = "El numero de cuenta solo debe contener digitos")
    @Schema(description = "Numero de cuenta activa del cliente desde la cual se descontara el monto", example = "1234567890")
    private String numeroCuenta;

    @NotNull(message = "El identificador del servicio (idServicio) es obligatorio")
    @Positive(message = "El idServicio debe ser un numero positivo")
    @Schema(description = "ID del servicio en el catalogo de GestoPago", example = "2284")
    private Integer idServicio;

    @NotNull(message = "El identificador del producto (idProducto) es obligatorio")
    @Positive(message = "El idProducto debe ser un numero positivo")
    @Schema(description = "ID del producto en el catalogo de GestoPago", example = "14302")
    private Integer idProducto;

    @NotNull(message = "El monto de pago es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto minimo permitido es de 1.00")
    @Digits(integer = 10, fraction = 2, message = "El monto debe tener hasta 10 digitos enteros y maximo 2 decimales")
    @Schema(description = "Monto de la operacion en MXN", example = "100.00")
    private BigDecimal montoPago;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "El telefono debe contener entre 10 y 15 digitos numericos")
    @Schema(description = "Numero telefonico destino (obligatorio en recargas de tiempo aire)", example = "5512345678")
    private String telefono;

    @Size(max = 100, message = "La referencia debe tener maximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]*$", message = "La referencia solo debe contener letras, numeros y guiones")
    @Schema(description = "Referencia del recibo o contrato (obligatoria en pagos de servicios)", example = "987654321012")
    private String referencia;

    @Size(min = 8, max = 100, message = "La clave de idempotencia debe tener entre 8 y 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]*$", message = "La clave de idempotencia solo permite letras, numeros, guiones y guion bajo")
    @Schema(description = "Clave unica opcional para prevenir cargos duplicados (tambien aceptada via header Idempotency-Key)", example = "recarga-20261030-0001")
    private String claveIdempotencia;
}
