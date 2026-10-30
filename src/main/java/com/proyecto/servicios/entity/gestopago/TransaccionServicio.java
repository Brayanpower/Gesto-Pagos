package com.proyecto.servicios.entity.gestopago;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacciones_servicio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "numero_cuenta", nullable = false, length = 20)
    private String numeroCuenta;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 100)
    private String claveIdempotencia;

    @Column(name = "upc", nullable = false, unique = true, length = 15)
    private String upc;

    @Column(name = "id_servicio", nullable = false)
    private Integer idServicio;

    @Column(name = "nombre_servicio", length = 150)
    private String nombreServicio;

    @Column(name = "id_producto", nullable = false)
    private Integer idProducto;

    @Column(name = "nombre_producto", length = 150)
    private String nombreProducto;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_anterior", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoPosterior;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 30)
    private EstatusTransaccion estatus;

    @Column(name = "codigo_respuesta_proveedor", length = 20)
    private String codigoRespuestaProveedor;

    @Column(name = "mensaje_proveedor", length = 500)
    private String mensajeProveedor;

    @Column(name = "folio_proveedor", length = 100)
    private String folioProveedor;

    @Column(name = "num_autorizacion", length = 100)
    private String numAutorizacion;

    @Column(name = "hora_local", nullable = false, length = 14)
    private String horaLocal;

    @Column(name = "intentos_conciliacion", nullable = false)
    private Integer intentosConciliacion = 0;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    public void prePersist() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.intentosConciliacion == null) {
            this.intentosConciliacion = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}

