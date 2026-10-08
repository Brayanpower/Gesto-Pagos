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
@Table(name = "catalogoMunicipio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoMunicipio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del municipio es obligatorio")
    @Size(max = 100, message = "El nombre del municipio no debe exceder 100 caracteres")
    @Column(nullable = false, length = 100, name = "municipio", columnDefinition = "TEXT")
    private String municipio;

    @NotNull(message = "El estado asociado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado", nullable = false)
    private CatalogoEstado catalogoEstado;
}
