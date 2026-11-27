package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.MfaRecoveryCode;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.repositorys.seguridad.MfaRecoveryCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de MfaRecoveryService")
class MfaRecoveryServiceTest {

    @Mock
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @InjectMocks
    private MfaRecoveryService service;

    @Test
    @DisplayName("generarCodigosRecuperacion borra previos, genera 8 unicos formato XXXX-XXXX y guarda solo hash SHA-256")
    @SuppressWarnings("unchecked")
    void generarCodigosRecuperacion_guardaHashesSHA256() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        when(recoveryCodeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<String> codigos = service.generarCodigosRecuperacion(usuario);

        assertEquals(8, codigos.size());
        Set<String> unicos = new HashSet<>(codigos);
        assertEquals(8, unicos.size());

        ArgumentCaptor<List<MfaRecoveryCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).deleteByUsuarioId(10L);
        verify(recoveryCodeRepository).saveAll(captor.capture());

        List<MfaRecoveryCode> guardados = captor.getValue();
        assertEquals(8, guardados.size());
        for (int i = 0; i < 8; i++) {
            String codigo = codigos.get(i);
            assertTrue(codigo.matches("^[A-Z2-9]{4}-[A-Z2-9]{4}$"));
            assertEquals(sha256Hex(codigo), guardados.get(i).getCodeHash());
            assertFalse(guardados.get(i).isUsado());
        }

        assertThrows(IllegalArgumentException.class, () -> service.generarCodigosRecuperacion(null));
    }

    @Test
    @DisplayName("consumirCodigoRecuperacion normaliza formato, marca como usado de un solo uso y rechaza invalidos")
    void consumirCodigoRecuperacion_consumeUnaSolaVez() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(10L);

        String hashEsperado = sha256Hex("ABCD-2345");
        MfaRecoveryCode entity = new MfaRecoveryCode();
        entity.setId(1L);
        entity.setUsuario(usuario);
        entity.setCodeHash(hashEsperado);
        entity.setUsado(false);

        when(recoveryCodeRepository.findByUsuarioIdAndCodeHashAndUsadoFalse(10L, hashEsperado))
                .thenReturn(Optional.of(entity))
                .thenReturn(Optional.empty());

        assertTrue(service.consumirCodigoRecuperacion(usuario, " abcd2345 "));
        assertTrue(entity.isUsado());
        assertNotNull(entity.getFechaUso());
        verify(recoveryCodeRepository).save(entity);

        assertFalse(service.consumirCodigoRecuperacion(usuario, "ABCD-2345"));
        assertFalse(service.consumirCodigoRecuperacion(usuario, "corto"));
        assertFalse(service.consumirCodigoRecuperacion(null, "ABCD-2345"));
    }

    @Test
    @DisplayName("invalidarCodigos delega eliminacion al repositorio cuando hay usuarioId")
    void invalidarCodigos_eliminaPorUsuarioId() {
        service.invalidarCodigos(null);
        service.invalidarCodigos(10L);
        verify(recoveryCodeRepository).deleteByUsuarioId(10L);
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
