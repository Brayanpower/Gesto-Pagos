package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEstadoRequest {

    @NotNull(message = "El id del usuario es obligatorio")
    @Positive(message = "El id del usuario debe ser un valor positivo")
    private Long id;

    @NotNull(message = "El estado del usuario (activo) es obligatorio")
    private Boolean activo;
}
