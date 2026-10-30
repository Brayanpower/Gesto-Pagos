package com.proyecto.servicios.model.catalogos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoEstadoResponse {
    private Long id;
    private String estado;
    private Long paisId;
    private String paisNombre;
}
