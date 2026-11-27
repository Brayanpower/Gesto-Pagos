package com.proyecto.servicios.repositorys.seguridad;

import com.proyecto.servicios.entity.seguridad.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    Optional<MfaRecoveryCode> findByUsuarioIdAndCodeHashAndUsadoFalse(Long usuarioId, String codeHash);

    long countByUsuarioIdAndUsadoFalse(Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MfaRecoveryCode c WHERE c.usuario.id = :usuarioId")
    int deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
