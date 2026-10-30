package com.proyecto.servicios.entity.clientes;

import com.proyecto.servicios.entity.catalogos.CatalogoColonia;
import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "domicilios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DomicilioCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, unique = true)
    private Cliente cliente;

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 100, message = "La calle no debe exceder 100 caracteres")
    @Column(nullable = false, length = 100)
    private String calle;

    @NotBlank(message = "El numero exterior es obligatorio")
    @Size(max = 10, message = "El numero exterior no debe exceder 10 caracteres")
    @Column(name = "no_exterior", nullable = false, length = 10)
    private String noExterior;

    @Size(max = 10, message = "El numero interior no debe exceder 10 caracteres")
    @Column(name = "no_interior", length = 10)
    private String noInterior;

    @NotNull(message = "La colonia es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colonia_id", nullable = false)
    private CatalogoColonia colonia;

    @NotNull(message = "El municipio es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipio_id", nullable = false)
    private CatalogoMunicipio municipio;

    @NotNull(message = "El estado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private CatalogoEstado estado;

    @NotBlank(message = "El codigo postal es obligatorio")
    @Pattern(regexp = "^[0-9]{5}$", message = "El codigo postal debe contener exactamente 5 digitos")
    @Column(nullable = false, length = 5)
    private String cp;

    @NotNull(message = "El pais es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pais_id", nullable = false)
    private CatalogoPais pais;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
