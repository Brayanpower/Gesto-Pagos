package com.proyecto.servicios.entity.cuentas;

import com.proyecto.servicios.entity.clientes.Cliente;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El numero de cuenta es obligatorio")
    @Size(max = 20, message = "El numero de cuenta no debe exceder 20 caracteres")
    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 20)
    private String numeroCuenta;

    @NotNull(message = "El cliente titular es obligatorio")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, unique = true)
    private Cliente cliente;

    @NotNull(message = "El tipo de cuenta es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private TipoCuenta tipoCuenta = TipoCuenta.AHORRO;

    @NotNull(message = "El saldo es obligatorio")
    @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @NotNull(message = "El estatus de la cuenta es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstatusCuenta estatus;

    @Column(name = "revisado_por", length = 100)
    private String revisadoPor;

    @Column(name = "observacion_revision", length = 255)
    private String observacionRevision;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now(ZoneId.systemDefault());
        if (this.tipoCuenta == null) {
            this.tipoCuenta = TipoCuenta.AHORRO;
        }
        if (this.estatus == null) {
            this.estatus = EstatusCuenta.PENDIENTE_REVISION;
        }
        if (this.saldo == null) {
            this.saldo = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now(ZoneId.systemDefault());
    }
}
