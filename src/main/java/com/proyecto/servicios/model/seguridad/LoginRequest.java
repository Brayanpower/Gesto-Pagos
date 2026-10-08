package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;
}
