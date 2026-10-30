package com.proyecto.servicios.model.catalogos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoGeneroRequest {

    @NotBlank(message = "El tipo de genero es obligatorio")
    @Size(max = 50, message = "El tipo de genero no debe exceder 50 caracteres")
    private String tipo;
    
    
    
}
