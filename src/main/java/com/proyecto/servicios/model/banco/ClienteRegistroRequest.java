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
public class ClienteRegistroRequest {

    // --- Datos Personales ---
    @NotBlank(message = "El primer nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El primer nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El primer nombre solo debe contener letras y espacios")
    private String nombre;

    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]*$", message = "El segundo nombre solo debe contener letras y espacios")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido paterno solo debe contener letras y espacios")
    private String apellidoPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido materno solo debe contener letras y espacios")
    private String apellidoMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;

    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(regexp = "(?i)^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[0-9A-Z][0-9]$", message = "El formato de CURP no es valido (18 caracteres)")
    private String curp;

    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "(?i)^[A-Z&Ñ]{3,4}[0-9]{6}[A-V1-9][A-Z0-9]{2}$", message = "El formato de RFC no es valido (12 o 13 caracteres)")
    private String rfc;

    @NotNull(message = "El id de genero es obligatorio")
    private Long generoId;

    @NotNull(message = "El id de nacionalidad es obligatorio")
    private Long nacionalidadId;

    @NotNull(message = "El id de estado civil es obligatorio")
    private Long estadoCivilId;

    // --- Datos de Contacto ---
    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "El telefono movil es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono movil debe tener exactamente 10 digitos")
    private String telefonoMovil;

    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono alternativo debe tener exactamente 10 digitos")
    private String telefonoAlternativo;

    // --- Información Laboral ---
    @NotBlank(message = "La ocupacion es obligatoria")
    @Size(max = 100, message = "La ocupacion no debe exceder 100 caracteres")
    private String ocupacion;

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    @Size(max = 250, message = "El nombre de la empresa no debe exceder 250 caracteres")
    private String empresa;

    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    private BigDecimal ingresoMensual;

    // --- Domicilio ---
    @NotNull(message = "Los datos de domicilio son obligatorios")
    @Valid
    private DomicilioDTO domicilio;

    // --- Cuenta Inicial y Credenciales ---
    @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo")
    private BigDecimal saldoInicial;

    @NotBlank(message = "La contrasena es obligatoria")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$",
            message = "La contrasena debe tener al menos 8 caracteres, incluyendo una mayuscula, una minuscula, un numero y un caracter especial")
    private String password;
}
