package com.proyecto.servicios.config;

import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@ConditionalOnBean(UsuarioRepository.class)
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapInitializer {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.correo:}")
    private String adminCorreo;

    @Value("${app.admin.bootstrap.password:}")
    private String adminPassword;

    public AdminBootstrapInitializer(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializarAdminSiNoExiste() {
        if (adminCorreo == null || adminCorreo.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Bootstrap de administrador habilitado sin credenciales configuradas; se omite creacion inicial.");
            return;
        }

        String correoNormalizado = adminCorreo.trim().toLowerCase();
        if (usuarioRepository.existsByCorreo(correoNormalizado)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setCliente(null);
        admin.setCorreo(correoNormalizado);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRol(AuthenticatedUserService.ROLE_ADMIN);
        admin.setActivo(true);

        usuarioRepository.save(admin);
        log.info("Usuario administrador inicial registrado para revision de onboarding: {}", correoNormalizado);
    }
}

