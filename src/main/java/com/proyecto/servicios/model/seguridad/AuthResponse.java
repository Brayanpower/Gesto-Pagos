package com.proyecto.servicios.model.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.GenericResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse extends GenericResponse {

    private String token;
    private String tipoToken = "Bearer";
    private String correo;
    private String rol;
    private Long clienteId;
    private String nombreCompleto;

    public AuthResponse(String token, String correo, String rol, Long clienteId, String nombreCompleto) {
        super();
        this.setCodigo(0);
        this.setMensaje("Autenticacion exitosa");
        this.token = token;
        this.tipoToken = "Bearer";
        this.correo = correo;
        this.rol = rol;
        this.clienteId = clienteId;
        this.nombreCompleto = nombreCompleto;
    }
}
