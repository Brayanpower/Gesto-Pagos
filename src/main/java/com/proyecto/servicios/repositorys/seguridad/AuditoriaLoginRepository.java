package com.proyecto.servicios.repositorys.seguridad;

import com.proyecto.servicios.entity.seguridad.AuditoriaLogin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditoriaLoginRepository extends JpaRepository<AuditoriaLogin, Long> {

    @EntityGraph(attributePaths = "usuario")
    Page<AuditoriaLogin> findByCorreoOrderByFechaCreacionDesc(String correo, Pageable pageable);

    @EntityGraph(attributePaths = "usuario")
    Page<AuditoriaLogin> findAllByOrderByFechaCreacionDesc(Pageable pageable);
}

