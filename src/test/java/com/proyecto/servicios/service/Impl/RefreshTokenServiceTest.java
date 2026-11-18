package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.seguridad.RefreshToken;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.RefreshTokenRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de RefreshTokenService")
class RefreshTokenServiceTest {

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private RefreshTokenService service;
    private Usuario usuario;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, usuarioRepository, REFRESH_EXPIRATION_MS);

        cliente = new Cliente();
        cliente.setId(50L);
        cliente.setActivo(true);

        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCorreo("cliente@banco.com");
        usuario.setRol("ROLE_CLIENTE");
        usuario.setActivo(true);
        usuario.setTokenVersion(0L);
        usuario.setCliente(cliente);
    }

    @Test
    @DisplayName("sha256Hex calcula resumen deterministico de 64 caracteres hexadecimales")
    void sha256Hex_deterministicoYValido() {
        String hash = service.sha256Hex("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
        assertEquals(64, hash.length());
        assertThrows(IllegalArgumentException.class, () -> service.sha256Hex(null));
    }

    @Test
    @DisplayName("emitirRefreshToken genera token opaco Base64URL y almacena unicamente hash SHA-256")
    void emitirRefreshToken_guardaHashYRetornaTokenOpaco() {
        ClientMetadata metadata = ClientMetadata.of("192.168.0.1", "Mozilla/5.0");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = service.emitirRefreshToken(usuario, metadata);

        assertNotNull(rawToken);
        assertFalse(rawToken.contains("="));
        assertTrue(rawToken.length() >= 40);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken guardado = captor.getValue();
        assertEquals(usuario, guardado.getUsuario());
        assertEquals(service.sha256Hex(rawToken), guardado.getTokenHash());
        assertNotEquals(rawToken, guardado.getTokenHash());
        assertNotNull(guardado.getFamiliaId());
        assertFalse(guardado.isRevocado());
        assertEquals("192.168.0.1", guardado.getIpOrigen());
        assertEquals("Mozilla/5.0", guardado.getUserAgent());
        assertTrue(guardado.getExpiraEn().isAfter(LocalDateTime.now(ZoneId.systemDefault()).plusDays(6)));
    }

    @Test
    @DisplayName("rotarRefreshToken revoca token actual, guarda reemplazadoPor y emite nuevo en la misma familia")
    void rotarRefreshToken_valido_rotaEnMismaFamilia() {
        String rawActual = "token-actual-opaco-1234567890";
        String hashActual = service.sha256Hex(rawActual);

        RefreshToken existente = new RefreshToken();
        existente.setId(1L);
        existente.setUsuario(usuario);
        existente.setFamiliaId("fam-001");
        existente.setTokenHash(hashActual);
        existente.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusHours(2));
        existente.setRevocado(false);

        when(refreshTokenRepository.findWithUsuarioByTokenHash(hashActual)).thenReturn(Optional.of(existente));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult result = service.rotarRefreshToken(
                rawActual, ClientMetadata.of("10.0.0.8", "MobileApp/1.0"));

        assertNotNull(result);
        assertEquals(usuario, result.usuario());
        assertNotNull(result.nuevoRefreshToken());
        assertNotEquals(rawActual, result.nuevoRefreshToken());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        List<RefreshToken> guardados = captor.getAllValues();
        RefreshToken actualizado = guardados.get(0);
        RefreshToken nuevo = guardados.get(1);

        String nuevoHash = service.sha256Hex(result.nuevoRefreshToken());
        assertTrue(actualizado.isRevocado());
        assertEquals(nuevoHash, actualizado.getReemplazadoPor());
        assertEquals("fam-001", nuevo.getFamiliaId());
        assertEquals(nuevoHash, nuevo.getTokenHash());
        assertFalse(nuevo.isRevocado());
        assertEquals("10.0.0.8", nuevo.getIpOrigen());
    }

    @Test
    @DisplayName("Deteccion de reuso revoca toda la familia, incrementa tokenVersion y lanza BadCredentialsException")
    void rotarRefreshToken_tokenYaRevocado_revocaFamiliaEIncrementaTokenVersion() {
        String rawReusado = "token-reusado-123456789012345";
        String hashReusado = service.sha256Hex(rawReusado);

        RefreshToken revocado = new RefreshToken();
        revocado.setId(2L);
        revocado.setUsuario(usuario);
        revocado.setFamiliaId("fam-comprometida");
        revocado.setTokenHash(hashReusado);
        revocado.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusHours(1));
        revocado.setRevocado(true);

        when(refreshTokenRepository.findWithUsuarioByTokenHash(hashReusado)).thenReturn(Optional.of(revocado));

        assertThrows(BadCredentialsException.class,
                () -> service.rotarRefreshToken(rawReusado, ClientMetadata.desconocido()));

        assertEquals(1L, usuario.getTokenVersion());
        verify(usuarioRepository).save(usuario);
        verify(refreshTokenRepository).revocarPorFamiliaId("fam-comprometida");
    }

    @Test
    @DisplayName("rotarRefreshToken expirado revoca token/familia y lanza BadCredentialsException")
    void rotarRefreshToken_expirado_revocaYLanzaBadCredentials() {
        String rawExpirado = "token-expirado-12345678901234";
        String hashExpirado = service.sha256Hex(rawExpirado);

        RefreshToken expirado = new RefreshToken();
        expirado.setId(3L);
        expirado.setUsuario(usuario);
        expirado.setFamiliaId("fam-expirada");
        expirado.setTokenHash(hashExpirado);
        expirado.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(1));
        expirado.setRevocado(false);

        when(refreshTokenRepository.findWithUsuarioByTokenHash(hashExpirado)).thenReturn(Optional.of(expirado));

        assertThrows(BadCredentialsException.class,
                () -> service.rotarRefreshToken(rawExpirado, ClientMetadata.desconocido()));

        assertTrue(expirado.isRevocado());
        verify(refreshTokenRepository).save(expirado);
        verify(refreshTokenRepository).revocarPorFamiliaId("fam-expirada");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotarRefreshToken con usuario o cliente inactivo revoca familia y lanza DisabledException")
    void rotarRefreshToken_usuarioOClienteInactivo_lanzaDisabledException() {
        String raw = "token-inactivo-12345678901234";
        String hash = service.sha256Hex(raw);

        cliente.setActivo(false);
        RefreshToken token = new RefreshToken();
        token.setId(4L);
        token.setUsuario(usuario);
        token.setFamiliaId("fam-inactiva");
        token.setTokenHash(hash);
        token.setExpiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusHours(2));
        token.setRevocado(false);

        when(refreshTokenRepository.findWithUsuarioByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThrows(DisabledException.class,
                () -> service.rotarRefreshToken(raw, ClientMetadata.desconocido()));

        assertTrue(token.isRevocado());
        verify(refreshTokenRepository).revocarPorFamiliaId("fam-inactiva");
    }

    @Test
    @DisplayName("rotarRefreshToken inexistente o en blanco lanza BadCredentialsException")
    void rotarRefreshToken_noExisteOBlanco_lanzaBadCredentials() {
        when(refreshTokenRepository.findWithUsuarioByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> service.rotarRefreshToken("desconocido-123456789012345", ClientMetadata.desconocido()));
        assertThrows(BadCredentialsException.class,
                () -> service.rotarRefreshToken("   ", ClientMetadata.desconocido()));
    }

    @Test
    @DisplayName("revocarToken y revocarTodasLasSesiones son idempotentes")
    void revocarTokenYRevocarTodasLasSesiones_funcionanIdempotentemente() {
        String raw = "token-logout-1234567890123456";
        String hash = service.sha256Hex(raw);

        RefreshToken activo = new RefreshToken();
        activo.setRevocado(false);
        when(refreshTokenRepository.findWithUsuarioByTokenHash(hash)).thenReturn(Optional.of(activo));

        service.revocarToken(raw);
        assertTrue(activo.isRevocado());
        verify(refreshTokenRepository).save(activo);

        // Segunda invocacion cuando ya esta revocado no reescribe
        service.revocarToken(raw);
        verify(refreshTokenRepository, times(1)).save(activo);

        service.revocarToken(null);
        service.revocarTodasLasSesiones(10L);
        verify(refreshTokenRepository).revocarTodosPorUsuarioId(10L);
        service.revocarTodasLasSesiones(null);
    }
}
