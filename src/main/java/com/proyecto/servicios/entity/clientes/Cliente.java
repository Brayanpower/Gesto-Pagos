package com.proyecto.servicios.entity.clientes;

import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import com.proyecto.servicios.entity.catalogos.CatalogoNacionalidad;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.seguridad.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Datos Personales ---
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El nombre solo debe contener letras y espacios")
    @Column(nullable = false, length = 50)
    private String nombre;

    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]*$", message = "El segundo nombre solo debe contener letras y espacios")
    @Column(name = "segundo_nombre", length = 50)
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido paterno solo debe contener letras y espacios")
    @Column(name = "apellido_paterno", nullable = false, length = 50)
    private String apellidoPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "El apellido materno solo debe contener letras y espacios")
    @Column(name = "apellido_materno", nullable = false, length = 50)
    private String apellidoMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser en el pasado")
    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(regexp = "^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[0-9A-Z][0-9]$", message = "El formato de CURP no es valido (18 caracteres)")
    @Column(nullable = false, unique = true, length = 18)
    private String curp;

    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "^[A-Z&Ñ]{3,4}[0-9]{6}[A-V1-9][A-Z0-9]{2}$", message = "El formato de RFC no es valido (12 o 13 caracteres)")
    @Column(nullable = false, unique = true, length = 13)
    private String rfc;

    @NotNull(message = "El genero es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genero_id", nullable = false)
    private CatalogoGeneros genero;

    @NotNull(message = "La nacionalidad es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nacionalidad_id", nullable = false)
    private CatalogoNacionalidad nacionalidad;

    @NotNull(message = "El estado civil es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_civil_id", nullable = false)
    private CatalogoEstadoCivil estadoCivil;

    // --- Datos de Contacto ---
    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El correo electronico no es valido")
    @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    @NotBlank(message = "El telefono movil es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono movil debe tener exactamente 10 digitos")
    @Column(name = "telefono_movil", nullable = false, length = 10)
    private String telefonoMovil;

    @Pattern(regexp = "^[0-9]{10}$", message = "El telefono alternativo debe tener exactamente 10 digitos")
    @Column(name = "telefono_alternativo", length = 10)
    private String telefonoAlternativo;

    // --- Información Laboral ---
    @NotBlank(message = "La ocupacion es obligatoria")
    @Size(max = 100, message = "La ocupacion no debe exceder 100 caracteres")
    @Column(nullable = false, length = 100)
    private String ocupacion;

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    @Size(max = 250, message = "El nombre de la empresa no debe exceder 250 caracteres")
    @Column(nullable = false, length = 250)
    private String empresa;

    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    @Column(name = "ingreso_mensual", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresoMensual;

    // --- Estado & Auditoría ---
    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    // --- Relaciones ---
    @OneToOne(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DomicilioCliente domicilio;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Cuenta> cuentas = new ArrayList<>();

    @OneToOne(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Usuario usuario;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
        this.activo = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
