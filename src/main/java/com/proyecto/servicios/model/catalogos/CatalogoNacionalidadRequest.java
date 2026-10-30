package com.proyecto.servicios.model.catalogos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoNacionalidadRequest {

    @NotBlank(message = "El nombre de la nacionalidad es obligatorio")
    @Size(max = 100, message = "El nombre de la nacionalidad no debe exceder 100 caracteres")
    private String nombre;
}
