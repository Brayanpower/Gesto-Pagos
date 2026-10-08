package com.proyecto.servicios.model.banco;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClienteUpdateRequest {

    @Size(min = 2, max = 50, message = "El primer nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El primer nombre solo debe contener letras y espacios")
    private String nombre;

    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]*$", message = "El segundo nombre solo debe contener letras y espacios")
    private String segundoNombre;

    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido paterno solo debe contener letras y espacios")
    private String apellidoPaterno;

    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido materno solo debe contener letras y espacios")
    private String apellidoMaterno;

    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;

    private Long generoId;

    private Long nacionalidadId;

    private Long estadoCivilId;

    @Email(message = "El correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono movil debe tener exactamente 10 digitos")
    private String telefonoMovil;

    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono alternativo debe tener exactamente 10 digitos")
    private String telefonoAlternativo;

    @Size(min = 1, max = 100, message = "La ocupacion debe tener entre 1 y 100 caracteres")
    private String ocupacion;

    @Size(min = 1, max = 250, message = "El nombre de la empresa debe tener entre 1 y 250 caracteres")
    private String empresa;

    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    private BigDecimal ingresoMensual;

    @Valid
    private DomicilioDTO domicilio;
}
