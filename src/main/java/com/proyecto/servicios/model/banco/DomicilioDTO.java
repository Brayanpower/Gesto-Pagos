package com.proyecto.servicios.model.banco;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomicilioDTO {

    private Long id;

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 100, message = "La calle no debe exceder 100 caracteres")
    private String calle;

    @NotBlank(message = "El numero exterior es obligatorio")
    @Size(max = 10, message = "El numero exterior no debe exceder 10 caracteres")
    private String noExterior;

    @Size(max = 10, message = "El numero interior no debe exceder 10 caracteres")
    private String noInterior;

    @NotNull(message = "El id de la colonia es obligatorio")
    private Long coloniaId;
    private String coloniaNombre;

    @NotNull(message = "El id del municipio es obligatorio")
    private Long municipioId;
    private String municipioNombre;

    @NotNull(message = "El id del estado es obligatorio")
    private Long estadoId;
    private String estadoNombre;

    @NotBlank(message = "El codigo postal es obligatorio")
    @Pattern(regexp = "^[0-9]{5}$", message = "El codigo postal debe contener exactamente 5 digitos numericos")
    private String cp;

    @NotNull(message = "El id del pais es obligatorio")
    private Long paisId;
    private String paisNombre;
}
