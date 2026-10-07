package com.proyecto.servicios.model.catalogos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoColoniaResponse {
    private Long id;
    private String colonia;
    private Long municipioId;
    private String municipioNombre;
}
