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
import com.proyecto.servicios.service.UsuarioService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class UsuarioServiceImpl implements UsuarioService {

    private static final String ROL_DEFAULT = "ROLE_CLIENTE";

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository,
            UsuarioMapper usuarioMapper,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse crear(UsuarioCreacionRequest request) {
        log.info("Iniciando alta de usuario con correo: {}", request.getCorreo());

        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + request.getClienteId()));

        if (!cliente.isActivo()) {
            throw new ReglaNegocioException("No es posible crear un usuario para un cliente inactivo (ID: " + cliente.getId() + ")");
        }

        String correo = request.getCorreo().trim().toLowerCase();

        if (usuarioRepository.existsByCorreo(correo) || clienteRepository.existsByCorreo(correo)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado con el correo: " + correo);
        }
        if (cliente.getUsuario() != null) {
            throw new DuplicateResourceException("El cliente con ID " + cliente.getId() + " ya tiene un usuario de acceso asignado");
        }

        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol((request.getRol() == null || request.getRol().isBlank()) ? ROL_DEFAULT : request.getRol().trim().toUpperCase());
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario creado exitosamente con ID {}", guardado.getId());

        return construirResponse(guardado, "Usuario creado exitosamente");
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioBusquedaResponse buscar(UsuarioBusquedaRequest request) {
        log.info("Busqueda de usuarios con filtros combinables");

        Specification<Usuario> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getId() != null) {
                predicates.add(cb.equal(root.get("id"), request.getId()));
            }
            if (request.getCorreo() != null && !request.getCorreo().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("correo")), request.getCorreo().trim().toLowerCase()));
            }
            if (request.getRol() != null && !request.getRol().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("rol")), request.getRol().trim().toUpperCase()));
            }
            if (request.getActivo() != null) {
                predicates.add(cb.equal(root.get("activo"), request.getActivo()));
            }
            if (request.getClienteId() != null) {
                predicates.add(cb.equal(root.get("cliente").get("id"), request.getClienteId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Usuario> usuarios = usuarioRepository.findAll(spec, Sort.by("id"));

        if (request.getId() != null && usuarios.isEmpty()) {
            throw new UsuarioNotFoundException(request.getId());
        }

        UsuarioBusquedaResponse response = new UsuarioBusquedaResponse();
        response.setCodigo(0);
        response.setMensaje("Consulta exitosa");
        response.setUsuarios(usuarioMapper.toResponses(usuarios));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse actualizar(UsuarioActualizacionRequest request) {
        log.info("Actualizacion de datos del usuario con ID: {}", request.getId());

        Usuario usuario = usuarioRepository.findById(request.getId())
                .orElseThrow(() -> new UsuarioNotFoundException(request.getId()));

        if (request.getCorreo() != null && !request.getCorreo().isBlank()) {
            String nuevoCorreo = request.getCorreo().trim().toLowerCase();
            if (!usuario.getCorreo().equalsIgnoreCase(nuevoCorreo)) {
                if (usuarioRepository.existsByCorreoAndIdNot(nuevoCorreo, usuario.getId())) {
                    throw new DuplicateResourceException("El correo electronico ya pertenece a otro usuario: " + nuevoCorreo);
                }
                usuario.setCorreo(nuevoCorreo);
            }
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRol() != null && !request.getRol().isBlank()) {
            usuario.setRol(request.getRol().trim().toUpperCase());
        }

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario con ID {} actualizado exitosamente", guardado.getId());

        return construirResponse(guardado, "Usuario actualizado exitosamente");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse cambiarEstado(UsuarioEstadoRequest request) {
        log.info("Cambiando estado del usuario con ID {} a activo={}", request.getId(), request.getActivo());

        Usuario usuario = usuarioRepository.findById(request.getId())
                .orElseThrow(() -> new UsuarioNotFoundException(request.getId()));

        if (Boolean.FALSE.equals(request.getActivo()) && estaAutenticadoComo(usuario.getCorreo())) {
            throw new ReglaNegocioException("No es posible desactivar la cuenta con la que se encuentra autenticado");
        }

        usuario.setActivo(request.getActivo());

        Usuario guardado = usuarioRepository.save(usuario);
        String mensaje = guardado.isActivo()
                ? "Usuario activado exitosamente"
                : "Usuario desactivado exitosamente";
        log.info("Usuario con ID {} {}", guardado.getId(), guardado.isActivo() ? "activado" : "desactivado");

        return construirResponse(guardado, mensaje);
    }

    private UsuarioResponse construirResponse(Usuario usuario, String mensaje) {
        UsuarioResponse response = usuarioMapper.toResponse(usuario);
        response.setCodigo(0);
        response.setMensaje(mensaje);
        return response;
    }

    private boolean estaAutenticadoComo(String correo) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null
                && autenticacion.isAuthenticated()
                && autenticacion.getName() != null
                && autenticacion.getName().equalsIgnoreCase(correo);
    }
}
