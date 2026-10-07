package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.CustomUserDetails;
import com.proyecto.servicios.security.JwtService;
import com.proyecto.servicios.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        log.info("Intento de inicio de sesion para correo: {}", correo);

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));

        if (!usuario.isActivo()) {
            log.warn("Intento de login con usuario inactivo: {}", correo);
            throw new DisabledException("El usuario se encuentra inactivo en el sistema");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            log.warn("Contrasena incorrecta para el usuario: {}", correo);
            throw new BadCredentialsException("Credenciales invalidas");
        }

        CustomUserDetails userDetails = new CustomUserDetails(usuario);
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("rol", usuario.getRol());
        if (usuario.getCliente() != null) {
            extraClaims.put("clienteId", usuario.getCliente().getId());
        }

        String token = jwtService.generateToken(extraClaims, userDetails);
        log.info("Token JWT generado exitosamente para el usuario: {}", correo);

        String nombreCompleto = usuario.getCliente() != null
                ? usuario.getCliente().getNombre() + " " + usuario.getCliente().getApellidoPaterno()
                : usuario.getCorreo();

        Long clienteId = usuario.getCliente() != null ? usuario.getCliente().getId() : null;

        return new AuthResponse(token, usuario.getCorreo(), usuario.getRol(), clienteId, nombreCompleto);
    }
}
