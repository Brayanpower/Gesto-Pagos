package com.proyecto.servicios.repositorys.catalogos;

import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogoGenerosRepository extends JpaRepository<CatalogoGeneros, Long> {

    Optional<CatalogoGeneros> findByTipo(String tipo);

    List<CatalogoGeneros> findByTipoContainingIgnoreCaseOrderByTipoAsc(String tipo);
}
