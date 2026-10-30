package com.proyecto.servicios.repositorys.seguridad;

import com.proyecto.servicios.entity.seguridad.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByCorreo(String correo);

    @EntityGraph(attributePaths = "cliente")
    Optional<Usuario> findWithClienteByCorreo(String correo);

    Optional<Usuario> findByCorreoAndActivoTrue(String correo);

    Optional<Usuario> findByClienteId(Long clienteId);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdNot(String correo, Long id);
}
