package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TotpVerificationRequest {

    @NotBlank(message = "El token de desafio es obligatorio")
    private String challengeToken;

    @NotBlank(message = "El codigo OTP es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El codigo OTP debe contener exactamente 6 digitos")
    private String codigo;
}

