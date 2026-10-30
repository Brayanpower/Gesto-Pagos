package com.proyecto.servicios.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetailResponse extends GenericResponse {
    private Map<String, String> errores;

    public ErrorDetailResponse(Integer codigo, String mensaje, Map<String, String> errores) {
        super();
        this.setCodigo(codigo);
        this.setMensaje(mensaje);
        this.errores = errores;
    }
}
