package com.proyecto.servicios.repositorys.seguridad;

import com.proyecto.servicios.entity.seguridad.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = {"usuario", "usuario.cliente"})
    Optional<RefreshToken> findWithUsuarioByTokenHash(String tokenHash);

    Optional<RefreshToken> findFirstByUsuarioIdAndRevocadoFalseAndExpiraEnAfterOrderByFechaCreacionDesc(
            Long usuarioId, LocalDateTime ahora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.usuario.id = :usuarioId AND rt.revocado = false")
    int revocarTodosPorUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.familiaId = :familiaId AND rt.revocado = false")
    int revocarPorFamiliaId(@Param("familiaId") String familiaId);
}

