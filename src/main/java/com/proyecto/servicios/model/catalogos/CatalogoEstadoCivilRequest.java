package com.proyecto.servicios.model.catalogos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoEstadoCivilRequest {

    @NotBlank(message = "El nombre del estado civil es obligatorio")
    @Size(max = 100, message = "El nombre del estado civil no debe exceder 100 caracteres")
    private String nombre;
}
