package com.proyecto.servicios.repositorys.seguridad;

import com.proyecto.servicios.entity.seguridad.MfaChallenge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {

    @EntityGraph(attributePaths = {"usuario", "usuario.cliente"})
    Optional<MfaChallenge> findWithUsuarioByChallengeHash(String challengeHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MfaChallenge mc SET mc.consumido = true WHERE mc.usuario.id = :usuarioId AND mc.consumido = false")
    int invalidarActivosPorUsuarioId(@Param("usuarioId") Long usuarioId);
}

