package com.proyecto.servicios.entity.catalogos;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalogoGenero")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoGeneros {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El tipo de genero es obligatorio")
    @Size(max = 50, message = "El tipo de genero no debe exceder 50 caracteres")
    @Column(nullable = false, length = 50, name = "tipo", columnDefinition = "TEXT")
    private String tipo;
}
