package com.proyecto.servicios.repositorys.catalogos;

import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogoEstadoCivilRepository extends JpaRepository<CatalogoEstadoCivil, Long> {

    Optional<CatalogoEstadoCivil> findByNombre(String nombre);

    List<CatalogoEstadoCivil> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
}
