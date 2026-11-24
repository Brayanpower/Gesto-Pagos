package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.AuditoriaLogin;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.AuditoriaLoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de LoginAuditService")
class LoginAuditServiceTest {

    @Mock
    private AuditoriaLoginRepository auditoriaLoginRepository;

    private LoginAuditService service;

    @BeforeEach
    void setUp() {
        service = new LoginAuditService(auditoriaLoginRepository);
    }

    @Test
    @DisplayName("registrarEvento normaliza correo a minusculas max 100 chars, nunca null y sanitiza detalle")
    void registrarEvento_normalizaCorreoYDetalle() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setCorreo("usuario@banco.com");

        String correoLargo = "  " + "A".repeat(120) + "@Banco.com\r\n";
        ClientMetadata metadata = ClientMetadata.of("10.1.2.3", "Agent/1.0");

        service.registrarEvento(
                usuario,
                correoLargo,
                LoginAuditService.EVENTO_PASSWORD_INVALIDA,
                false,
                metadata,
                "Bearer secreto123 en intento\nfallido");

        ArgumentCaptor<AuditoriaLogin> captor = ArgumentCaptor.forClass(AuditoriaLogin.class);
        verify(auditoriaLoginRepository).save(captor.capture());

        AuditoriaLogin guardado = captor.getValue();
        assertEquals(usuario, guardado.getUsuario());
        assertNotNull(guardado.getCorreo());
        assertEquals(100, guardado.getCorreo().length());
        assertEquals("a".repeat(100), guardado.getCorreo());
        assertEquals(LoginAuditService.EVENTO_PASSWORD_INVALIDA, guardado.getEvento());
        assertFalse(guardado.isExitoso());
        assertEquals("10.1.2.3", guardado.getIpOrigen());
        assertEquals("Agent/1.0", guardado.getUserAgent());
        assertFalse(guardado.getDetalle().contains("secreto123"));
        assertTrue(guardado.getDetalle().contains("[REDACTED]"));
    }

    @Test
    @DisplayName("registrarEvento usa correo de usuario o 'desconocido' cuando el correo recibido es nulo o blanco")
    void registrarEvento_correoNuloOBlanco_nuncaGuardaNull() {
        service.registrarEvento(null, null, null, false, null, null);

        ArgumentCaptor<AuditoriaLogin> captor = ArgumentCaptor.forClass(AuditoriaLogin.class);
        verify(auditoriaLoginRepository).save(captor.capture());

        AuditoriaLogin guardado = captor.getValue();
        assertEquals("desconocido", guardado.getCorreo());
        assertEquals("DESCONOCIDO", guardado.getEvento());
        assertNull(guardado.getIpOrigen());
        assertNull(guardado.getDetalle());
    }

    @Test
    @DisplayName("consultarAuditoria filtra por correo normalizado o consulta todo segun parametro")
    void consultarAuditoria_conYSinFiltroCorreo() {
        Usuario usuario = new Usuario();
        usuario.setId(9L);

        AuditoriaLogin row = new AuditoriaLogin();
        row.setId(1L);
        row.setUsuario(usuario);
        row.setCorreo("admin@banco.com");
        row.setEvento(LoginAuditService.EVENTO_EXITO);
        row.setExitoso(true);
        row.setIpOrigen("127.0.0.1");
        row.setFechaCreacion(LocalDateTime.now(ZoneId.systemDefault()));

        Page<AuditoriaLogin> page = new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1);
        when(auditoriaLoginRepository.findByCorreoOrderByFechaCreacionDesc(eq("admin@banco.com"), any(Pageable.class)))
                .thenReturn(page);
        when(auditoriaLoginRepository.findAllByOrderByFechaCreacionDesc(any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AuditoriaLoginResponse> filtrada = service.consultarAuditoria("  ADMIN@Banco.com ", PageRequest.of(0, 10));
        assertEquals(1, filtrada.getContenido().size());
        assertEquals(9L, filtrada.getContenido().get(0).getUsuarioId());
        assertEquals(LoginAuditService.EVENTO_EXITO, filtrada.getContenido().get(0).getEvento());

        PageResponse<AuditoriaLoginResponse> todas = service.consultarAuditoria("  ", PageRequest.of(0, 10));
        assertEquals(1, todas.getContenido().size());
        verify(auditoriaLoginRepository).findAllByOrderByFechaCreacionDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("consultarAuditoria rechaza tamanos de pagina fuera de 1..100")
    void consultarAuditoria_sizeFueraDeRango_lanzaExcepcion() {
        Pageable sizeExcedido = PageRequest.of(0, 101);
        assertThrows(ReglaNegocioException.class,
                () -> service.consultarAuditoria(null, sizeExcedido));
    }
}
