package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El formato del correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 128, message = "La contrasena no debe exceder 128 caracteres")
    private String password;

    @Pattern(regexp = "^\\d{6}$", message = "El codigo OTP debe contener exactamente 6 digitos")
    private String codigoTotp;

    private Boolean forzarCierreSesionAnterior;

    public LoginRequest(String correo, String password) {
        this.correo = correo;
        this.password = password;
    }

    public LoginRequest(String correo, String password, String codigoTotp) {
        this.correo = correo;
        this.password = password;
        this.codigoTotp = codigoTotp;
    }
}
