package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.catalogos.*;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.DomicilioCliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.cuentas.TipoCuenta;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.mapper.DomicilioClienteMapper;
import com.proyecto.servicios.model.banco.ClienteRegistroRequest;
import com.proyecto.servicios.model.banco.ClienteResponse;
import com.proyecto.servicios.model.banco.ClienteUpdateRequest;
import com.proyecto.servicios.model.banco.DomicilioDTO;
import com.proyecto.servicios.repositorys.catalogos.*;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.DomicilioClienteRepository;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import com.proyecto.servicios.service.ClienteService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ClienteServiceImpl implements ClienteService {

    private static final String CLIENTE_NO_ENCONTRADO = "Cliente activo no encontrado con ID: ";

    private final ClienteRepository clienteRepository;
    private final DomicilioClienteRepository domicilioClienteRepository;
    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;

    private final CatalogoGenerosRepository generosRepository;
    private final CatalogoNacionalidadRepository nacionalidadRepository;
    private final CatalogoEstadoCivilRepository estadoCivilRepository;
    private final CatalogoColoniaRepository coloniaRepository;
    private final CatalogoMunicipioRepository municipioRepository;
    private final CatalogoEstadoRepository estadoRepository;
    private final CatalogoPaisRepository paisRepository;

    private final ClienteMapper clienteMapper;
    private final DomicilioClienteMapper domicilioClienteMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticatedUserService authenticatedUserService;

    private final SecureRandom secureRandom = new SecureRandom();

    public ClienteServiceImpl(
            ClienteRepository clienteRepository,
            DomicilioClienteRepository domicilioClienteRepository,
            CuentaRepository cuentaRepository,
            UsuarioRepository usuarioRepository,
            CatalogoGenerosRepository generosRepository,
            CatalogoNacionalidadRepository nacionalidadRepository,
            CatalogoEstadoCivilRepository estadoCivilRepository,
            CatalogoColoniaRepository coloniaRepository,
            CatalogoMunicipioRepository municipioRepository,
            CatalogoEstadoRepository estadoRepository,
            CatalogoPaisRepository paisRepository,
            ClienteMapper clienteMapper,
            DomicilioClienteMapper domicilioClienteMapper,
            PasswordEncoder passwordEncoder,
            AuthenticatedUserService authenticatedUserService) {
        this.clienteRepository = clienteRepository;
        this.domicilioClienteRepository = domicilioClienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.generosRepository = generosRepository;
        this.nacionalidadRepository = nacionalidadRepository;
        this.estadoCivilRepository = estadoCivilRepository;
        this.coloniaRepository = coloniaRepository;
        this.municipioRepository = municipioRepository;
        this.estadoRepository = estadoRepository;
        this.paisRepository = paisRepository;
        this.clienteMapper = clienteMapper;
        this.domicilioClienteMapper = domicilioClienteMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClienteResponse registrarCliente(ClienteRegistroRequest request) {
        log.info("Iniciando proceso transaccional de registro de cliente con CURP: {}", request.getCurp());

        // 1. Regla de negocio: Mayoria de edad (18 anos o mas) calculada dinamicamente
        validarMayoriaDeEdad(request.getFechaNacimiento());

        // 2. Validacion de unicidad en BD
        String curp = request.getCurp().trim().toUpperCase();
        String rfc = request.getRfc().trim().toUpperCase();
        String correo = request.getCorreo().trim().toLowerCase();

        if (clienteRepository.existsByCurp(curp)) {
            throw new DuplicateResourceException("Ya existe un cliente registrado con la CURP: " + curp);
        }
        if (clienteRepository.existsByRfc(rfc)) {
            throw new DuplicateResourceException("Ya existe un cliente registrado con el RFC: " + rfc);
        }
        if (clienteRepository.existsByCorreo(correo) || usuarioRepository.existsByCorreo(correo)) {
            throw new DuplicateResourceException("Ya existe un cliente o usuario registrado con el correo: " + correo);
        }

        // 3. Validacion y obtencion de catalogos personales
        CatalogoGeneros genero = generosRepository.findById(request.getGeneroId())
                .orElseThrow(() -> new CatalogoNotFoundException("Genero no encontrado con id: " + request.getGeneroId()));
        CatalogoNacionalidad nacionalidad = nacionalidadRepository.findById(request.getNacionalidadId())
                .orElseThrow(() -> new CatalogoNotFoundException("Nacionalidad no encontrada con id: " + request.getNacionalidadId()));
        CatalogoEstadoCivil estadoCivil = estadoCivilRepository.findById(request.getEstadoCivilId())
                .orElseThrow(() -> new CatalogoNotFoundException("Estado civil no encontrado con id: " + request.getEstadoCivilId()));

        // 4. Paso 1: Persistir Cliente
        Cliente cliente = clienteMapper.toEntity(request);
        cliente.setCurp(curp);
        cliente.setRfc(rfc);
        cliente.setCorreo(correo);
        cliente.setGenero(genero);
        cliente.setNacionalidad(nacionalidad);
        cliente.setEstadoCivil(estadoCivil);
        cliente.setActivo(true);

        Cliente clienteGuardado = clienteRepository.save(cliente);
        log.info("Paso 1/4 exitoso: Cliente persistido con ID {}", clienteGuardado.getId());

        // 5. Paso 2: Persistir Domicilio vinculado (1:1)
        DomicilioDTO domDto = request.getDomicilio();
        CatalogoColonia colonia = coloniaRepository.findById(domDto.getColoniaId())
                .orElseThrow(() -> new CatalogoNotFoundException("Colonia no encontrada con id: " + domDto.getColoniaId()));
        CatalogoMunicipio municipio = municipioRepository.findById(domDto.getMunicipioId())
                .orElseThrow(() -> new CatalogoNotFoundException("Municipio no encontrado con id: " + domDto.getMunicipioId()));
        CatalogoEstado estado = estadoRepository.findById(domDto.getEstadoId())
                .orElseThrow(() -> new CatalogoNotFoundException("Estado no encontrado con id: " + domDto.getEstadoId()));
        CatalogoPais pais = paisRepository.findById(domDto.getPaisId())
                .orElseThrow(() -> new CatalogoNotFoundException("Pais no encontrado con id: " + domDto.getPaisId()));

        DomicilioCliente domicilio = domicilioClienteMapper.toEntity(domDto);
        domicilio.setCliente(clienteGuardado);
        domicilio.setColonia(colonia);
        domicilio.setMunicipio(municipio);
        domicilio.setEstado(estado);
        domicilio.setPais(pais);

        DomicilioCliente domicilioGuardado = domicilioClienteRepository.save(domicilio);
        clienteGuardado.setDomicilio(domicilioGuardado);
        log.info("Paso 2/4 exitoso: Domicilio persistido con ID {}", domicilioGuardado.getId());

        // 6. Paso 3: Crear Cuenta de Ahorro unica inicial (Estatus PENDIENTE_REVISION para onboarding)
        BigDecimal saldoInicial = (request.getSaldoInicial() != null) ? request.getSaldoInicial() : BigDecimal.ZERO;
        String numeroCuenta = generarNumeroCuentaUnico();

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(clienteGuardado);
        cuenta.setTipoCuenta(TipoCuenta.AHORRO);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setSaldo(saldoInicial);
        cuenta.setEstatus(EstatusCuenta.PENDIENTE_REVISION);

        Cuenta cuentaGuardada = cuentaRepository.save(cuenta);
        clienteGuardado.setCuenta(cuentaGuardada);
        log.info("Paso 3/4 exitoso: Cuenta de ahorro {} creada en PENDIENTE_REVISION con saldo {}", numeroCuenta, saldoInicial);

        // 7. Paso 4: Crear Usuario de Acceso automatico (Password BCrypt)
        Usuario usuario = new Usuario();
        usuario.setCliente(clienteGuardado);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(AuthenticatedUserService.ROLE_CLIENTE);
        usuario.setActivo(true);

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        clienteGuardado.setUsuario(usuarioGuardado);
        log.info("Paso 4/4 exitoso: Usuario de acceso creado con ID {}", usuarioGuardado.getId());

        ClienteResponse response = clienteMapper.toResponse(clienteGuardado);
        response.setCodigo(0);
        response.setMensaje("Cliente, domicilio, cuenta de ahorro unica (pendiente de revision) y usuario registrados exitosamente");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerMiPerfil() {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esCliente(usuario) || usuario.getCliente() == null) {
            throw new AccessDeniedException("Solo un cliente con perfil asociado puede consultar su perfil de cliente.");
        }
        Cliente cliente = buscarClienteActivo(usuario.getCliente().getId());
        ClienteResponse response = clienteMapper.toResponse(cliente);
        response.setCodigo(0);
        response.setMensaje("Consulta exitosa");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorId(Long id) {
        validarAccesoCliente(id);
        Cliente cliente = buscarClienteActivo(id);
        ClienteResponse response = clienteMapper.toResponse(cliente);
        response.setCodigo(0);
        response.setMensaje("Consulta exitosa");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> buscarConFiltros(
            String nombre,
            String apellidoPaterno,
            String apellidoMaterno,
            String curp,
            String rfc,
            String correo) {

        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esAdmin(usuario)) {
            throw new AccessDeniedException("Solo un administrador puede consultar el listado general de clientes.");
        }

        Specification<Cliente> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("activo")));

            agregarFiltroLike(predicates, cb, root.get("nombre"), nombre);
            agregarFiltroLike(predicates, cb, root.get("apellidoPaterno"), apellidoPaterno);
            agregarFiltroLike(predicates, cb, root.get("apellidoMaterno"), apellidoMaterno);
            agregarFiltroEqual(predicates, cb, root.get("curp"), curp, true);
            agregarFiltroEqual(predicates, cb, root.get("rfc"), rfc, true);
            agregarFiltroEqual(predicates, cb, root.get("correo"), correo, false);

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Cliente> clientes = clienteRepository.findAll(spec);
        return clienteMapper.toResponses(clientes);
    }

    private void agregarFiltroLike(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                  jakarta.persistence.criteria.Expression<String> expr, String valor) {
        if (valor != null && !valor.isBlank()) {
            predicates.add(cb.like(cb.lower(expr), "%" + valor.trim().toLowerCase() + "%"));
        }
    }

    private void agregarFiltroEqual(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Expression<String> expr, String valor, boolean toUpper) {
        if (valor != null && !valor.isBlank()) {
            String normalizado = toUpper ? valor.trim().toUpperCase() : valor.trim().toLowerCase();
            predicates.add(cb.equal(toUpper ? cb.upper(expr) : cb.lower(expr), normalizado));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClienteResponse actualizar(Long id, ClienteUpdateRequest request) {
        validarAccesoCliente(id);
        log.info("Actualizando informacion permitida para el cliente con ID: {}", id);
        Cliente cliente = buscarClienteActivo(id);

        if (request.getFechaNacimiento() != null) {
            validarMayoriaDeEdad(request.getFechaNacimiento());
        }

        actualizarCorreoSiAplica(cliente, request.getCorreo());
        actualizarCatalogosRelacionados(cliente, request);
        clienteMapper.updateEntity(request, cliente);
        actualizarDomicilioCliente(cliente, request.getDomicilio());

        Cliente guardado = clienteRepository.save(cliente);
        ClienteResponse response = clienteMapper.toResponse(guardado);
        response.setCodigo(0);
        response.setMensaje("Cliente actualizado exitosamente");
        return response;
    }

    private void actualizarCorreoSiAplica(Cliente cliente, String nuevoCorreoRaw) {
        if (nuevoCorreoRaw == null) {
            return;
        }
        String nuevoCorreo = nuevoCorreoRaw.trim().toLowerCase();
        if (!cliente.getCorreo().equalsIgnoreCase(nuevoCorreo)) {
            if (clienteRepository.existsByCorreo(nuevoCorreo) || usuarioRepository.existsByCorreo(nuevoCorreo)) {
                throw new DuplicateResourceException("El correo electronico ya pertenece a otro cliente/usuario: " + nuevoCorreo);
            }
            cliente.setCorreo(nuevoCorreo);
            if (cliente.getUsuario() != null) {
                cliente.getUsuario().setCorreo(nuevoCorreo);
            }
        }
    }

    private void actualizarCatalogosRelacionados(Cliente cliente, ClienteUpdateRequest request) {
        if (request.getGeneroId() != null) {
            CatalogoGeneros genero = generosRepository.findById(request.getGeneroId())
                    .orElseThrow(() -> new CatalogoNotFoundException("Genero no encontrado con id: " + request.getGeneroId()));
            cliente.setGenero(genero);
        }
        if (request.getNacionalidadId() != null) {
            CatalogoNacionalidad nacionalidad = nacionalidadRepository.findById(request.getNacionalidadId())
                    .orElseThrow(() -> new CatalogoNotFoundException("Nacionalidad no encontrada con id: " + request.getNacionalidadId()));
            cliente.setNacionalidad(nacionalidad);
        }
        if (request.getEstadoCivilId() != null) {
            CatalogoEstadoCivil estadoCivil = estadoCivilRepository.findById(request.getEstadoCivilId())
                    .orElseThrow(() -> new CatalogoNotFoundException("Estado civil no encontrado con id: " + request.getEstadoCivilId()));
            cliente.setEstadoCivil(estadoCivil);
        }
    }

    private void actualizarDomicilioCliente(Cliente cliente, DomicilioDTO domDto) {
        if (domDto == null || cliente.getDomicilio() == null) {
            return;
        }
        CatalogoColonia colonia = coloniaRepository.findById(domDto.getColoniaId())
                .orElseThrow(() -> new CatalogoNotFoundException("Colonia no encontrada con id: " + domDto.getColoniaId()));
        CatalogoMunicipio municipio = municipioRepository.findById(domDto.getMunicipioId())
                .orElseThrow(() -> new CatalogoNotFoundException("Municipio no encontrado con id: " + domDto.getMunicipioId()));
        CatalogoEstado estado = estadoRepository.findById(domDto.getEstadoId())
                .orElseThrow(() -> new CatalogoNotFoundException("Estado no encontrado con id: " + domDto.getEstadoId()));
        CatalogoPais pais = paisRepository.findById(domDto.getPaisId())
                .orElseThrow(() -> new CatalogoNotFoundException("Pais no encontrado con id: " + domDto.getPaisId()));

        domicilioClienteMapper.updateEntity(domDto, cliente.getDomicilio());
        cliente.getDomicilio().setColonia(colonia);
        cliente.getDomicilio().setMunicipio(municipio);
        cliente.getDomicilio().setEstado(estado);
        cliente.getDomicilio().setPais(pais);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void darDeBaja(Long id) {
        Usuario operador = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esAdmin(operador)) {
            throw new AccessDeniedException("Solo un administrador puede dar de baja a un cliente.");
        }

        log.info("Iniciando baja logica para el cliente con ID: {}", id);
        Cliente cliente = buscarClienteActivo(id);

        // 1. Desactivar cliente
        cliente.setActivo(false);

        // 2. Desactivar usuario de acceso asociado e invalidar tokens activos
        if (cliente.getUsuario() != null) {
            cliente.getUsuario().setActivo(false);
            cliente.getUsuario().incrementarTokenVersion();
            usuarioRepository.save(cliente.getUsuario());
            log.info("Usuario de acceso ID {} desactivado por baja logica de cliente", cliente.getUsuario().getId());
        }

        // 3. Inactivar su unica cuenta de ahorro si no esta cancelada ni rechazada
        Cuenta cuenta = cliente.getCuenta();
        if (cuenta != null
                && cuenta.getEstatus() != EstatusCuenta.CANCELADA
                && cuenta.getEstatus() != EstatusCuenta.RECHAZADA
                && cuenta.getEstatus() != EstatusCuenta.INACTIVA) {
            cuenta.setEstatus(EstatusCuenta.INACTIVA);
            cuentaRepository.save(cuenta);
            log.info("Cuenta de ahorro {} inactivada por baja logica de cliente", cuenta.getNumeroCuenta());
        }

        clienteRepository.save(cliente);
        log.info("Baja logica del cliente ID {} completada exitosamente", id);
    }

    private void validarAccesoCliente(Long clienteIdSolicitado) {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (authenticatedUserService.esAdmin(usuario)) {
            return;
        }
        if (usuario.getCliente() == null || !usuario.getCliente().getId().equals(clienteIdSolicitado)) {
            throw new AccessDeniedException("No tiene autorizacion para consultar o modificar informacion de otro cliente.");
        }
    }

    private Cliente buscarClienteActivo(Long id) {
        return clienteRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(CLIENTE_NO_ENCONTRADO + id));
    }

    private void validarMayoriaDeEdad(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria");
        }
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        if (fechaNacimiento.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura");
        }
        int edad = Period.between(fechaNacimiento, hoy).getYears();
        if (edad < 18) {
            throw new IllegalArgumentException("El cliente debe ser mayor de edad (minimo 18 anos). Edad calculada: " + edad + " anos");
        }
    }

    private String generarNumeroCuentaUnico() {
        String numeroCuenta;
        do {
            long numero = 1000000000L + (long) (secureRandom.nextDouble() * 9000000000L);
            numeroCuenta = String.valueOf(numero);
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        return numeroCuenta;
    }
}
