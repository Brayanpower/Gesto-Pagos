package com.proyecto.servicios.model.catalogos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoMunicipioRequest {

    @NotBlank(message = "El nombre del municipio es obligatorio")
    @Size(max = 100, message = "El nombre del municipio no debe exceder 100 caracteres")
    private String municipio;

    @NotNull(message = "El ID del estado es obligatorio")
    private Long estadoId;
}
