package com.proyecto.servicios.model.catalogos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoPaisRequest {

    @NotBlank(message = "El nombre del pais es obligatorio")
    @Size(max = 100, message = "El nombre del pais no debe exceder 100 caracteres")
    private String nombre;
}
