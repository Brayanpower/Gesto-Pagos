package com.proyecto.servicios.model.banco;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.catalogos.CatalogoEstadoCivilResponse;
import com.proyecto.servicios.model.catalogos.CatalogoGeneroResponse;
import com.proyecto.servicios.model.catalogos.CatalogoNacionalidadResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClienteResponse extends GenericResponse {

    private Long id;
    private String nombre;
    private String segundoNombre;
    private String apellidoPaterno;
    private String apellidoMaterno;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;

    private String curp;
    private String rfc;

    private CatalogoGeneroResponse genero;
    private CatalogoNacionalidadResponse nacionalidad;
    private CatalogoEstadoCivilResponse estadoCivil;

    private String correo;
    private String telefonoMovil;
    private String telefonoAlternativo;

    private String ocupacion;
    private String empresa;
    private BigDecimal ingresoMensual;

    private boolean activo;

    private DomicilioDTO domicilio;
    private List<CuentaResponse> cuentas;
    private UsuarioResponseDTO usuario;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaCreacion;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaActualizacion;
}
