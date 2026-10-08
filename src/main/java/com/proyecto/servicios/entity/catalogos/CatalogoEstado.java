package com.proyecto.servicios.entity.catalogos;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalogoEstado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del estado es obligatorio")
    @Size(max = 100, message = "El nombre del estado no debe exceder 100 caracteres")
    @Column(nullable = false, length = 100, name = "estado", columnDefinition = "TEXT")
    private String estado;

    @NotNull(message = "El pais asociado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pais", nullable = false)
    private CatalogoPais catalogoPais;
}
