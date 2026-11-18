package com.proyecto.servicios.config;

import com.proyecto.servicios.entity.catalogos.CatalogoColonia;
import com.proyecto.servicios.entity.catalogos.CatalogoEstado;
import com.proyecto.servicios.entity.catalogos.CatalogoEstadoCivil;
import com.proyecto.servicios.entity.catalogos.CatalogoGeneros;
import com.proyecto.servicios.entity.catalogos.CatalogoMunicipio;
import com.proyecto.servicios.entity.catalogos.CatalogoNacionalidad;
import com.proyecto.servicios.entity.catalogos.CatalogoPais;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.DomicilioCliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.cuentas.TipoCuenta;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.DomicilioClienteRepository;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnBean({
        UsuarioRepository.class,
        ClienteRepository.class,
        DomicilioClienteRepository.class,
        CuentaRepository.class
})
@ConditionalOnProperty(name = "app.demo.cliente.bootstrap.enabled", havingValue = "true")
public class DemoClienteBootstrapInitializer {

    private static final String DEMO_CURP = "MERC950515HDFNRR01";
    private static final String DEMO_RFC = "MERC950515AB1";
    private static final String CUENTA_INICIAL_PREFERIDA = "4000100001";
    private static final BigDecimal SALDO_POR_DEFECTO = new BigDecimal("5000.00");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final DomicilioClienteRepository domicilioClienteRepository;
    private final CuentaRepository cuentaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Value("${app.demo.cliente.bootstrap.correo:}")
    private String clienteCorreo;

    @Value("${app.demo.cliente.bootstrap.password:}")
    private String clientePassword;

    @Value("${app.demo.cliente.bootstrap.saldo-inicial:5000.00}")
    private BigDecimal saldoInicial = SALDO_POR_DEFECTO;

    @Value("${app.admin.bootstrap.correo:admin@banco.com}")
    private String adminCorreo = "admin@banco.com";

    public DemoClienteBootstrapInitializer(
            UsuarioRepository usuarioRepository,
            ClienteRepository clienteRepository,
            DomicilioClienteRepository domicilioClienteRepository,
            CuentaRepository cuentaRepository,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.domicilioClienteRepository = domicilioClienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializarClienteDemoSiNoExiste() {
        if (clienteCorreo == null || clienteCorreo.isBlank()
                || clientePassword == null || clientePassword.isBlank()) {
            log.warn("Bootstrap de cliente demo habilitado sin credenciales configuradas; se omite creacion inicial.");
            return;
        }

        String correoNormalizado = clienteCorreo.trim().toLowerCase(Locale.ROOT);
        if (yaExisteClienteOUsuario(correoNormalizado)) {
            return;
        }

        Optional<CatalogosDemo> catalogosOpt = cargarCatalogosBase();
        if (catalogosOpt.isEmpty()) {
            log.warn("Catalogos base incompletos; se omite creacion automatica del cliente demo {}", correoNormalizado);
            return;
        }

        CatalogosDemo catalogos = catalogosOpt.get();
        Cliente clienteGuardado = crearYGuardarCliente(correoNormalizado, catalogos);
        crearYGuardarDomicilio(clienteGuardado, catalogos);
        Cuenta cuentaGuardada = crearYGuardarCuentaActiva(clienteGuardado);
        crearYGuardarUsuario(clienteGuardado, correoNormalizado);

        log.info("Cliente demo inicializado para QA: correo={}, numeroCuenta={}, estatus={}, saldo={}",
                correoNormalizado, cuentaGuardada.getNumeroCuenta(),
                cuentaGuardada.getEstatus(), cuentaGuardada.getSaldo());
    }

    private boolean yaExisteClienteOUsuario(String correoNormalizado) {
        return usuarioRepository.existsByCorreo(correoNormalizado)
                || clienteRepository.existsByCorreo(correoNormalizado)
                || clienteRepository.existsByCurp(DEMO_CURP)
                || clienteRepository.existsByRfc(DEMO_RFC);
    }

    private Optional<CatalogosDemo> cargarCatalogosBase() {
        Optional<CatalogoGeneros> genero = obtenerPrimero(CatalogoGeneros.class);
        Optional<CatalogoNacionalidad> nacionalidad = obtenerPrimero(CatalogoNacionalidad.class);
        Optional<CatalogoEstadoCivil> estadoCivil = obtenerPrimero(CatalogoEstadoCivil.class);
        Optional<CatalogoColonia> coloniaOpt = obtenerPrimero(CatalogoColonia.class);

        if (genero.isEmpty() || nacionalidad.isEmpty() || estadoCivil.isEmpty() || coloniaOpt.isEmpty()) {
            return Optional.empty();
        }

        CatalogoColonia colonia = coloniaOpt.get();
        CatalogoMunicipio municipio = colonia.getCatalogoMunicipio();
        CatalogoEstado estado = municipio != null ? municipio.getCatalogoEstado() : null;
        CatalogoPais pais = estado != null ? estado.getCatalogoPais() : null;
        if (municipio == null || estado == null || pais == null) {
            return Optional.empty();
        }

        return Optional.of(new CatalogosDemo(
                genero.get(),
                nacionalidad.get(),
                estadoCivil.get(),
                colonia,
                municipio,
                estado,
                pais
        ));
    }

    private <T> Optional<T> obtenerPrimero(Class<T> entityClass) {
        String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e ORDER BY e.id ASC";
        List<T> rows = entityManager.createQuery(jpql, entityClass)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Cliente crearYGuardarCliente(String correoNormalizado, CatalogosDemo catalogos) {
        Cliente cliente = new Cliente();
        cliente.setNombre("Carlos");
        cliente.setApellidoPaterno("Mendoza");
        cliente.setApellidoMaterno("Ruiz");
        cliente.setFechaNacimiento(LocalDate.of(1995, 5, 15));
        cliente.setCurp(DEMO_CURP);
        cliente.setRfc(DEMO_RFC);
        cliente.setGenero(catalogos.genero());
        cliente.setNacionalidad(catalogos.nacionalidad());
        cliente.setEstadoCivil(catalogos.estadoCivil());
        cliente.setCorreo(correoNormalizado);
        cliente.setTelefonoMovil("5512345678");
        cliente.setOcupacion("Ingeniero");
        cliente.setEmpresa("BancaCore QA");
        cliente.setIngresoMensual(new BigDecimal("25000.00"));
        cliente.setActivo(true);
        return clienteRepository.save(cliente);
    }

    private void crearYGuardarDomicilio(Cliente cliente, CatalogosDemo catalogos) {
        DomicilioCliente domicilio = new DomicilioCliente();
        domicilio.setCliente(cliente);
        domicilio.setCalle("Av Reforma");
        domicilio.setNoExterior("100");
        domicilio.setColonia(catalogos.colonia());
        domicilio.setMunicipio(catalogos.municipio());
        domicilio.setEstado(catalogos.estado());
        domicilio.setPais(catalogos.pais());
        domicilio.setCp("06600");
        DomicilioCliente guardado = domicilioClienteRepository.save(domicilio);
        cliente.setDomicilio(guardado);
    }

    private Cuenta crearYGuardarCuentaActiva(Cliente cliente) {
        BigDecimal saldo = (saldoInicial != null && saldoInicial.compareTo(BigDecimal.ZERO) >= 0)
                ? saldoInicial
                : SALDO_POR_DEFECTO;

        Cuenta cuenta = new Cuenta();
        cuenta.setCliente(cliente);
        cuenta.setTipoCuenta(TipoCuenta.AHORRO);
        cuenta.setNumeroCuenta(resolverNumeroCuentaDisponible());
        cuenta.setSaldo(saldo);
        cuenta.setEstatus(EstatusCuenta.ACTIVA);
        cuenta.setRevisadoPor(adminCorreo != null && !adminCorreo.isBlank() ? adminCorreo.trim().toLowerCase(Locale.ROOT) : "bootstrap-qa");
        cuenta.setObservacionRevision("Cuenta demo activada automaticamente para validacion");
        cuenta.setFechaRevision(LocalDateTime.now(ZoneId.systemDefault()));

        Cuenta guardada = cuentaRepository.save(cuenta);
        cliente.setCuenta(guardada);
        return guardada;
    }

    private void crearYGuardarUsuario(Cliente cliente, String correoNormalizado) {
        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo(correoNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(clientePassword));
        usuario.setRol(AuthenticatedUserService.ROLE_CLIENTE);
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.save(usuario);
        cliente.setUsuario(guardado);
    }

    private String resolverNumeroCuentaDisponible() {
        if (!cuentaRepository.existsByNumeroCuenta(CUENTA_INICIAL_PREFERIDA)) {
            return CUENTA_INICIAL_PREFERIDA;
        }
        String candidato;
        do {
            long numero = 4000000000L + SECURE_RANDOM.nextInt(999_999_999);
            candidato = String.valueOf(numero);
        } while (cuentaRepository.existsByNumeroCuenta(candidato));
        return candidato;
    }

    private record CatalogosDemo(
            CatalogoGeneros genero,
            CatalogoNacionalidad nacionalidad,
            CatalogoEstadoCivil estadoCivil,
            CatalogoColonia colonia,
            CatalogoMunicipio municipio,
            CatalogoEstado estado,
            CatalogoPais pais) {
    }
}

