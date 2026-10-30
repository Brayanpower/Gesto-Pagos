package com.proyecto.servicios.repositorys.catalogos;

import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogoPaisRepository extends JpaRepository<CatalogoPais, Long> {

    Optional<CatalogoPais> findByNombre(String nombre);

    List<CatalogoPais> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
}
