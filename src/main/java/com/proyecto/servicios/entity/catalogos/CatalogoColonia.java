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
@Table(name = "catalogoColonia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoColonia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la colonia es obligatorio")
    @Size(max = 100, message = "El nombre de la colonia no debe exceder 100 caracteres")
    @Column(nullable = false, length = 100, name = "colonia", columnDefinition = "TEXT")
    private String colonia;

    @NotNull(message = "El municipio asociado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipio", nullable = false)
    private CatalogoMunicipio catalogoMunicipio;
}
