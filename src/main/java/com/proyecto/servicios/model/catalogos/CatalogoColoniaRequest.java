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
public class CatalogoColoniaRequest {

    @NotBlank(message = "El nombre de la colonia es obligatorio")
    @Size(max = 100, message = "El nombre de la colonia no debe exceder 100 caracteres")
    private String colonia;

    @NotNull(message = "El ID del municipio es obligatorio")
    private Long municipioId;
}
