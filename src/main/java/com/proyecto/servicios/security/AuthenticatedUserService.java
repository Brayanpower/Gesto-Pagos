package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_CLIENTE = "ROLE_CLIENTE";

    private final UsuarioRepository usuarioRepository;

    public AuthenticatedUserService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario obtenerUsuarioAutenticadoActivo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new InsufficientAuthenticationException("Se requiere autenticacion valida para acceder a este recurso.");
        }

        Usuario usuario;
        if (auth.getPrincipal() instanceof CustomUserDetails customDetails) {
            usuario = usuarioRepository.findWithClienteByCorreo(customDetails.getUsername())
                    .orElse(customDetails.getUsuario());
        } else {
            String correo = auth.getName();
            usuario = usuarioRepository.findWithClienteByCorreo(correo)
                    .orElseThrow(() -> new InsufficientAuthenticationException("Usuario autenticado no encontrado."));
        }

        if (!usuario.isActivo()) {
            throw new DisabledException("El usuario se encuentra inactivo para realizar operaciones.");
        }
        return usuario;
    }

    public boolean esAdmin(Usuario usuario) {
        return usuario != null && ROLE_ADMIN.equalsIgnoreCase(usuario.getRol());
    }

    public boolean esCliente(Usuario usuario) {
        return usuario != null && ROLE_CLIENTE.equalsIgnoreCase(usuario.getRol());
    }
}

