package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioBusquedaRequest {

    @Positive(message = "El id del usuario debe ser un valor positivo")
    private Long id;

    @Email(message = "El correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @Pattern(regexp = "^(ROLE_CLIENTE|ROLE_ADMIN)$", message = "El rol debe ser ROLE_CLIENTE o ROLE_ADMIN")
    private String rol;

    private Boolean activo;

    @Positive(message = "El id del cliente debe ser un valor positivo")
    private Long clienteId;
}
