package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailOtpSendRequest {

    @NotBlank(message = "El token de desafio es obligatorio")
    private String challengeToken;
}
