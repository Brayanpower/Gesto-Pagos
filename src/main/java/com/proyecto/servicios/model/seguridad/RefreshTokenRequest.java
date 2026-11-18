package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "El refresh token es obligatorio")
    @Size(min = 20, max = 512, message = "El refresh token debe tener entre 20 y 512 caracteres")
    private String refreshToken;
}

