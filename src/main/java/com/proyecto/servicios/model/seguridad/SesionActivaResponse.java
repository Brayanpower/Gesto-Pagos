package com.proyecto.servicios.model.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SesionActivaResponse {

    private boolean activa;
    private String correo;
    private String ipOrigen;
    private String userAgent;
    private LocalDateTime fechaInicio;
    private LocalDateTime expiraEn;
    private Long tokenVersion;
}
