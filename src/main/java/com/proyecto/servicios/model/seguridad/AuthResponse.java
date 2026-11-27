package com.proyecto.servicios.model.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.GenericResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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
    private String refreshToken;
    private Long expiresInSegundos;
    private Boolean requiereTotp;
    private Boolean totpConfiguracionRequerida;
    private String challengeToken;
    private String totpSecret;
    private String otpauthUri;
    private Boolean otpCorreoDisponible;
    private Boolean otpCorreoEnviado;
    private List<String> codigosRecuperacion;

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
