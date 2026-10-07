package com.proyecto.servicios.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EliminaPersonaRequest {

    @NotBlank(message = "El nombre es obligatorio para eliminar el registro")
    private String nombre;
}
