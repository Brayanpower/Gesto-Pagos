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
public class CatalogoEstadoRequest {

    @NotBlank(message = "El nombre del estado es obligatorio")
    @Size(max = 100, message = "El nombre del estado no debe exceder 100 caracteres")
    private String estado;

    @NotNull(message = "El ID del pais es obligatorio")
    private Long paisId;
}
