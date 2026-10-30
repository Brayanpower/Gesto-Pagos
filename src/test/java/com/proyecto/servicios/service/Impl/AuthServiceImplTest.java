package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de AuthServiceImpl")
class AuthServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl servicio;

    @Test
    @DisplayName("Login correcto genera token y devuelve datos del cliente")
    void login_correcto_devuelveToken() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreo("juan.garcia@banco.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Banco123@", "$2a$10$hash")).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = servicio.login(new LoginRequest("Juan.Garcia@Banco.com", "Banco123@"));

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("juan.garcia@banco.com", response.getCorreo());
        assertEquals("ROLE_CLIENTE", response.getRol());
        assertEquals(1L, response.getClienteId());
    }

    @Test
    @DisplayName("Login con contrasena incorrecta lanza BadCredentialsException")
    void login_passwordIncorrecta_lanzaExcepcion() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> servicio.login(new LoginRequest("juan.garcia@banco.com", "Incorrecta1@")));
    }

    @Test
    @DisplayName("Login con usuario inexistente lanza BadCredentialsException")
    void login_usuarioInexistente_lanzaExcepcion() {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> servicio.login(new LoginRequest("nadie@banco.com", "Banco123@")));
    }

    @Test
    @DisplayName("Login con usuario inactivo lanza DisabledException")
    void login_usuarioInactivo_lanzaExcepcion() {
        Usuario usuario = usuarioActivo();
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.of(usuario));

        assertThrows(DisabledException.class,
                () -> servicio.login(new LoginRequest("juan.garcia@banco.com", "Banco123@")));
    }

    private static Usuario usuarioActivo() {
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Juan");
        cliente.setApellidoPaterno("Garcia");

        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCorreo("juan.garcia@banco.com");
        usuario.setPasswordHash("$2a$10$hash");
        usuario.setRol("ROLE_CLIENTE");
        usuario.setActivo(true);
        usuario.setCliente(cliente);
        return usuario;
    }
}
