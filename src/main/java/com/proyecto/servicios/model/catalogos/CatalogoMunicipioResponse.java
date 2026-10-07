package com.proyecto.servicios.model.catalogos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CatalogoMunicipioResponse {
    private Long id;
    private String municipio;
    private Long estadoId;
    private String estadoNombre;
}
