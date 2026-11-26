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
import com.proyecto.servicios.repositorys.seguridad.MfaRecoveryCodeRepository;
import com.proyecto.servicios.repositorys.seguridad.RefreshTokenRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MfaRecoveryCodeRepository mfaRecoveryCodeRepository;

    public UsuarioServiceImpl(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository,
            UsuarioMapper usuarioMapper,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            MfaRecoveryCodeRepository mfaRecoveryCodeRepository) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.mfaRecoveryCodeRepository = mfaRecoveryCodeRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse crear(UsuarioCreacionRequest request) {
        log.info("Iniciando alta de usuario con correo: {}", request.getCorreo());

        String rol = resolverRolCreacion(request);
        String correo = request.getCorreo().trim().toLowerCase();

        if (usuarioRepository.existsByCorreo(correo) || clienteRepository.existsByCorreo(correo)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado con el correo: " + correo);
        }

        Cliente cliente = resolverClienteParaRol(rol, request.getClienteId());

        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(rol);
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario creado exitosamente con ID {} y rol {}", guardado.getId(), guardado.getRol());

        return construirResponse(guardado, "Usuario creado exitosamente");
    }

    private String resolverRolCreacion(UsuarioCreacionRequest request) {
        if (request.getRol() == null || request.getRol().isBlank()) {
            return request.getClienteId() == null
                    ? AuthenticatedUserService.ROLE_ADMIN
                    : AuthenticatedUserService.ROLE_CLIENTE;
        }
        return request.getRol().trim().toUpperCase();
    }

    private Cliente resolverClienteParaRol(String rol, Long clienteId) {
        if (AuthenticatedUserService.ROLE_ADMIN.equals(rol)) {
            if (clienteId != null) {
                throw new ReglaNegocioException("Un usuario ROLE_ADMIN no puede estar vinculado a un cliente ni a una cuenta bancaria.");
            }
            return null;
        }

        if (clienteId == null) {
            throw new ReglaNegocioException("Un usuario ROLE_CLIENTE debe asociarse a un cliente existente.");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + clienteId));

        if (!cliente.isActivo()) {
            throw new ReglaNegocioException("No es posible crear un usuario para un cliente inactivo (ID: " + cliente.getId() + ")");
        }
        if (cliente.getUsuario() != null) {
            throw new DuplicateResourceException("El cliente con ID " + cliente.getId() + " ya tiene un usuario de acceso asignado");
        }
        return cliente;
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

        boolean correoActualizado = false;
        if (request.getCorreo() != null && !request.getCorreo().isBlank()) {
            String nuevoCorreo = request.getCorreo().trim().toLowerCase();
            if (!usuario.getCorreo().equalsIgnoreCase(nuevoCorreo)) {
                boolean duplicadoEnUsuario = usuarioRepository.existsByCorreoAndIdNot(nuevoCorreo, usuario.getId());
                boolean duplicadoEnCliente = usuario.getCliente() != null
                        ? clienteRepository.existsByCorreoAndIdNot(nuevoCorreo, usuario.getCliente().getId())
                        : clienteRepository.existsByCorreo(nuevoCorreo);
                if (duplicadoEnUsuario || duplicadoEnCliente) {
                    throw new DuplicateResourceException("El correo electronico ya pertenece a otro usuario: " + nuevoCorreo);
                }
                usuario.setCorreo(nuevoCorreo);
                if (usuario.getCliente() != null) {
                    usuario.getCliente().setCorreo(nuevoCorreo);
                    clienteRepository.save(usuario.getCliente());
                }
                usuario.incrementarTokenVersion();
                correoActualizado = true;
            }
        }

        boolean passwordActualizado = false;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            if (!correoActualizado) {
                usuario.incrementarTokenVersion();
            }
            passwordActualizado = true;
        }

        if (request.getRol() != null && !request.getRol().isBlank()) {
            String rolSolicitado = request.getRol().trim().toUpperCase();
            if (!usuario.getRol().equalsIgnoreCase(rolSolicitado)) {
                throw new ReglaNegocioException("No esta permitido cambiar el rol de un usuario existente.");
            }
        }

        Usuario guardado = usuarioRepository.save(usuario);
        if (passwordActualizado || correoActualizado) {
            refreshTokenRepository.revocarTodosPorUsuarioId(usuario.getId());
        }
        log.info("Usuario con ID {} actualizado exitosamente", guardado.getId());

        return construirResponse(guardado, "Usuario actualizado exitosamente");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse cambiarEstado(UsuarioEstadoRequest request) {
        log.info("Cambiando estado del usuario con ID {} a activo={}", request.getId(), request.getActivo());

        Usuario usuario = usuarioRepository.findById(request.getId())
                .orElseThrow(() -> new UsuarioNotFoundException(request.getId()));

        boolean desactivando = Boolean.FALSE.equals(request.getActivo());
        if (desactivando && estaAutenticadoComo(usuario.getCorreo())) {
            throw new ReglaNegocioException("No es posible desactivar la cuenta con la que se encuentra autenticado");
        }

        usuario.setActivo(request.getActivo());
        if (desactivando) {
            usuario.incrementarTokenVersion();
        }

        Usuario guardado = usuarioRepository.save(usuario);
        if (desactivando) {
            refreshTokenRepository.revocarTodosPorUsuarioId(usuario.getId());
        }
        String mensaje = guardado.isActivo()
                ? "Usuario activado exitosamente"
                : "Usuario desactivado exitosamente";
        log.info("Usuario con ID {} {}", guardado.getId(), guardado.isActivo() ? "activado" : "desactivado");

        return construirResponse(guardado, mensaje);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsuarioResponse resetearTotp(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNotFoundException(usuarioId));

        usuario.setTotpHabilitado(false);
        usuario.setTotpSecretEnc(null);
        usuario.setTotpConfiguradoEn(null);
        usuario.setTotpUltimoPaso(null);
        usuario.incrementarTokenVersion();

        Usuario guardado = usuarioRepository.save(usuario);
        refreshTokenRepository.revocarTodosPorUsuarioId(usuarioId);
        mfaRecoveryCodeRepository.deleteByUsuarioId(usuarioId);
        log.info("TOTP restablecido por administrador para usuario ID {}", usuarioId);

        return construirResponse(
                guardado,
                "TOTP restablecido; el usuario debera reconfigurar Google Authenticator en su proximo inicio de sesion");
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
