package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.exception.UsuarioNotFoundException;
import com.proyecto.servicios.mapper.UsuarioMapper;
import com.proyecto.servicios.model.seguridad.UsuarioActualizacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.seguridad.UsuarioBusquedaResponse;
import com.proyecto.servicios.model.seguridad.UsuarioCreacionRequest;
import com.proyecto.servicios.model.seguridad.UsuarioEstadoRequest;
import com.proyecto.servicios.model.seguridad.UsuarioResponse;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de UsuarioServiceImpl")
class UsuarioServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl servicio;

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Creacion exitosa: cifra la contrasena, asigna rol por defecto y activo en servidor")
    void crear_exito_cifraPasswordYAsignaRolPorDefecto() {
        UsuarioCreacionRequest request = creacionValida();
        Cliente cliente = clienteActivo(1L);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Banco123@")).thenReturn("$2a$10$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponse());

        UsuarioResponse response = servicio.crear(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals("$2a$10$hash", guardado.getPasswordHash());
        assertEquals("ROLE_CLIENTE", guardado.getRol());
        assertTrue(guardado.isActivo());
        assertEquals("cliente@banco.com", guardado.getCorreo());
    }

    @Test
    @DisplayName("Creacion con cliente inexistente lanza ResourceNotFoundException")
    void crear_clienteInexistente_lanzaExcepcion() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.crear(creacionValida()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Creacion con cliente inactivo viola la regla del sistema (ReglaNegocioException)")
    void crear_clienteInactivo_lanzaReglaNegocio() {
        Cliente cliente = clienteActivo(1L);
        cliente.setActivo(false);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        assertThrows(ReglaNegocioException.class, () -> servicio.crear(creacionValida()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Creacion con correo duplicado lanza DuplicateResourceException")
    void crear_correoDuplicado_lanzaExcepcion() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteActivo(1L)));
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.crear(creacionValida()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Creacion cuando el cliente ya tiene usuario lanza DuplicateResourceException")
    void crear_clienteYaTieneUsuario_lanzaExcepcion() {
        Cliente cliente = clienteActivo(1L);
        cliente.setUsuario(new Usuario());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(false);

        assertThrows(DuplicateResourceException.class, () -> servicio.crear(creacionValida()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Busqueda combinada devuelve los usuarios filtrados")
    void buscar_filtrosCombinados_retornaLista() {
        UsuarioBusquedaRequest request = new UsuarioBusquedaRequest();
        request.setRol("ROLE_CLIENTE");
        request.setActivo(true);
        request.setCorreo("cliente@banco.com");

        when(usuarioRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(usuarioConId(10L), usuarioConId(11L)));
        when(usuarioMapper.toResponses(any(List.class))).thenReturn(List.of(new UsuarioResponse(), new UsuarioResponse()));

        UsuarioBusquedaResponse response = servicio.buscar(request);

        assertEquals(0, response.getCodigo());
        assertEquals("Consulta exitosa", response.getMensaje());
        assertEquals(2, response.getUsuarios().size());
        verify(usuarioRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("Busqueda por id inexistente lanza UsuarioNotFoundException (404)")
    void buscar_idInexistente_lanzaUsuarioNotFound() {
        UsuarioBusquedaRequest request = new UsuarioBusquedaRequest();
        request.setId(999L);

        when(usuarioRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThrows(UsuarioNotFoundException.class, () -> servicio.buscar(request));
    }

    @Test
    @DisplayName("Actualizacion con id inexistente lanza UsuarioNotFoundException (404)")
    void actualizar_idInexistente_lanzaUsuarioNotFound() {
        when(usuarioRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNotFoundException.class, () -> servicio.actualizar(actualizacionValida()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Actualizacion con correo ya usado por otro usuario lanza DuplicateResourceException (409)")
    void actualizar_correoDeOtroUsuario_lanzaDuplicado() {
        Usuario existente = usuarioConId(10L);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByCorreoAndIdNot("otro@banco.com", 10L)).thenReturn(true);

        UsuarioActualizacionRequest request = actualizacionValida();
        request.setCorreo("otro@banco.com");

        assertThrows(DuplicateResourceException.class, () -> servicio.actualizar(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Actualizacion exitosa cifra la nueva contrasena y aplica el rol")
    void actualizar_exito_cifraPasswordYAplicaRol() {
        Usuario existente = usuarioConId(10L);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(passwordEncoder.encode("Banco123@")).thenReturn("$2a$10$nuevoHash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponse());

        UsuarioResponse response = servicio.actualizar(actualizacionValida());

        assertEquals(0, response.getCodigo());
        assertEquals("$2a$10$nuevoHash", existente.getPasswordHash());
        assertEquals("ROLE_ADMIN", existente.getRol());
    }

    @Test
    @DisplayName("Cambio de estado con id inexistente lanza UsuarioNotFoundException (404)")
    void cambiarEstado_idInexistente_lanzaUsuarioNotFound() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNotFoundException.class, () -> servicio.cambiarEstado(estadoRequest(999L, false)));
    }

    @Test
    @DisplayName("Desactivar otro usuario funciona y responde codigo 0")
    void cambiarEstado_desactivarOtroUsuario_exitoso() {
        Usuario existente = usuarioConId(10L);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponse());

        autenticarComo("otro@banco.com");
        UsuarioResponse response = servicio.cambiarEstado(estadoRequest(10L, false));

        assertEquals(0, response.getCodigo());
        assertFalse(existente.isActivo());
    }

    @Test
    @DisplayName("Desactivar la cuenta autenticada viola la regla del sistema (ReglaNegocioException)")
    void cambiarEstado_desactivarseASiMismo_lanzaReglaNegocio() {
        Usuario existente = usuarioConId(10L);
        existente.setCorreo("cliente@banco.com");
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(existente));

        autenticarComo("cliente@banco.com");

        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarEstado(estadoRequest(10L, false)));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    private UsuarioCreacionRequest creacionValida() {
        UsuarioCreacionRequest request = new UsuarioCreacionRequest();
        request.setClienteId(1L);
        request.setCorreo("cliente@banco.com");
        request.setPassword("Banco123@");
        return request;
    }

    private UsuarioActualizacionRequest actualizacionValida() {
        UsuarioActualizacionRequest request = new UsuarioActualizacionRequest();
        request.setId(10L);
        request.setPassword("Banco123@");
        request.setRol("ROLE_ADMIN");
        return request;
    }

    private UsuarioEstadoRequest estadoRequest(Long id, boolean activo) {
        return new UsuarioEstadoRequest(id, activo);
    }

    private Cliente clienteActivo(Long id) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setActivo(true);
        return cliente;
    }

    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCorreo("cliente@banco.com");
        usuario.setRol("ROLE_CLIENTE");
        usuario.setActivo(true);
        return usuario;
    }

    private void autenticarComo(String correo) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(correo, "n/a", List.of()));
    }
}
