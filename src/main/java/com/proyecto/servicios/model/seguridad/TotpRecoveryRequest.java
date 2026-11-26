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
public class TotpRecoveryRequest {

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El formato del correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 128, message = "La contrasena no debe exceder 128 caracteres")
    private String password;

    @NotBlank(message = "El codigo de recuperacion es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}$",
            message = "El codigo de recuperacion debe tener formato XXXX-XXXX")
    private String codigoRecuperacion;
}
