package com.proyecto.servicios.model.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
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
public class UsuarioActualizacionRequest {

    @NotNull(message = "El id del usuario es obligatorio")
    @Positive(message = "El id del usuario debe ser un valor positivo")
    private Long id;

    @Email(message = "El correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$",
            message = "La contrasena debe tener al menos 8 caracteres, incluyendo una mayuscula, una minuscula, un numero y un caracter especial")
    private String password;

    @Pattern(regexp = "^(ROLE_CLIENTE|ROLE_ADMIN)$", message = "El rol debe ser ROLE_CLIENTE o ROLE_ADMIN")
    private String rol;
}
