package com.proyecto.servicios.service.Impl;

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
import com.proyecto.servicios.exception.CatalogoNotFoundException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.mapper.DomicilioClienteMapper;
import com.proyecto.servicios.model.banco.ClienteRegistroRequest;
import com.proyecto.servicios.model.banco.ClienteResponse;
import com.proyecto.servicios.model.banco.ClienteUpdateRequest;
import com.proyecto.servicios.model.banco.DomicilioDTO;
import com.proyecto.servicios.repositorys.catalogos.CatalogoColoniaRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoCivilRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoEstadoRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoGenerosRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoMunicipioRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoNacionalidadRepository;
import com.proyecto.servicios.repositorys.catalogos.CatalogoPaisRepository;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.DomicilioClienteRepository;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.seguridad.UsuarioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de ClienteServiceImpl")
class ClienteServiceImplTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private DomicilioClienteRepository domicilioClienteRepository;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CatalogoGenerosRepository generosRepository;
    @Mock private CatalogoNacionalidadRepository nacionalidadRepository;
    @Mock private CatalogoEstadoCivilRepository estadoCivilRepository;
    @Mock private CatalogoColoniaRepository coloniaRepository;
    @Mock private CatalogoMunicipioRepository municipioRepository;
    @Mock private CatalogoEstadoRepository estadoRepository;
    @Mock private CatalogoPaisRepository paisRepository;
    @Mock private ClienteMapper clienteMapper;
    @Mock private DomicilioClienteMapper domicilioClienteMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private ClienteServiceImpl servicio;

    @Test
    @DisplayName("Onboarding exitoso crea cliente, domicilio, unica cuenta AHORRO en PENDIENTE_REVISION y usuario ROLE_CLIENTE")
    void registrarCliente_exito_creaTodoElFlujo() {
        ClienteRegistroRequest request = requestValido();

        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(false);
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(generosRepository.findById(1L)).thenReturn(Optional.of(new CatalogoGeneros()));
        when(nacionalidadRepository.findById(1L)).thenReturn(Optional.of(new CatalogoNacionalidad()));
        when(estadoCivilRepository.findById(1L)).thenReturn(Optional.of(new CatalogoEstadoCivil()));
        when(clienteMapper.toEntity(any())).thenReturn(new Cliente());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(coloniaRepository.findById(1L)).thenReturn(Optional.of(new CatalogoColonia()));
        when(municipioRepository.findById(1L)).thenReturn(Optional.of(new CatalogoMunicipio()));
        when(estadoRepository.findById(1L)).thenReturn(Optional.of(new CatalogoEstado()));
        when(paisRepository.findById(1L)).thenReturn(Optional.of(new CatalogoPais()));
        when(domicilioClienteMapper.toEntity(any())).thenReturn(new DomicilioCliente());
        when(domicilioClienteRepository.save(any(DomicilioCliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("Banco123@")).thenReturn("$2a$10$hash");
        when(clienteMapper.toResponse(any(Cliente.class))).thenReturn(new ClienteResponse());

        ClienteResponse response = servicio.registrarCliente(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());

        ArgumentCaptor<Cuenta> cuentaCaptor = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).save(cuentaCaptor.capture());
        Cuenta cuenta = cuentaCaptor.getValue();
        assertEquals(TipoCuenta.AHORRO, cuenta.getTipoCuenta());
        assertEquals(EstatusCuenta.PENDIENTE_REVISION, cuenta.getEstatus());
        assertEquals(new BigDecimal("1000.00"), cuenta.getSaldo());
        assertEquals(10, cuenta.getNumeroCuenta().length());

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuario = usuarioCaptor.getValue();
        assertEquals("$2a$10$hash", usuario.getPasswordHash());
        assertNotEquals("Banco123@", usuario.getPasswordHash());
        assertTrue(usuario.isActivo());
        assertEquals("ROLE_CLIENTE", usuario.getRol());

        verify(clienteRepository).save(any(Cliente.class));
        verify(domicilioClienteRepository).save(any(DomicilioCliente.class));
    }

    @Test
    @DisplayName("Registro de menor de edad lanza IllegalArgumentException")
    void registrarCliente_menorDeEdad_lanzaExcepcion() {
        ClienteRegistroRequest request = requestValido();
        request.setFechaNacimiento(LocalDate.now().minusYears(10));

        assertThrows(IllegalArgumentException.class, () -> servicio.registrarCliente(request));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("CURP duplicada lanza DuplicateResourceException")
    void registrarCliente_curpDuplicada_lanzaExcepcion() {
        ClienteRegistroRequest request = requestValido();
        when(clienteRepository.existsByCurp(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.registrarCliente(request));
    }

    @Test
    @DisplayName("RFC duplicado lanza DuplicateResourceException")
    void registrarCliente_rfcDuplicado_lanzaExcepcion() {
        ClienteRegistroRequest request = requestValido();
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.registrarCliente(request));
    }

    @Test
    @DisplayName("Correo duplicado lanza DuplicateResourceException")
    void registrarCliente_correoDuplicado_lanzaExcepcion() {
        ClienteRegistroRequest request = requestValido();
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> servicio.registrarCliente(request));
    }

    @Test
    @DisplayName("Catalogo inexistente lanza CatalogoNotFoundException")
    void registrarCliente_catalogoInexistente_lanzaExcepcion() {
        ClienteRegistroRequest request = requestValido();
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(clienteRepository.existsByCorreo(anyString())).thenReturn(false);
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(generosRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(CatalogoNotFoundException.class, () -> servicio.registrarCliente(request));
    }

    @Test
    @DisplayName("Consulta de cliente activo por su titular devuelve la respuesta mapeada")
    void obtenerPorId_existente_devuelveCliente() {
        Cliente cliente = new Cliente();
        cliente.setId(5L);
        Usuario usuario = usuarioCliente(cliente);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(usuario);
        when(authenticatedUserService.esAdmin(usuario)).thenReturn(false);

        when(clienteRepository.findByIdAndActivoTrue(5L)).thenReturn(Optional.of(cliente));
        ClienteResponse expected = new ClienteResponse();
        expected.setId(5L);
        when(clienteMapper.toResponse(cliente)).thenReturn(expected);

        ClienteResponse response = servicio.obtenerPorId(5L);

        assertEquals(5L, response.getId());
        assertEquals(0, response.getCodigo());
    }

    @Test
    @DisplayName("Cliente no puede consultar perfil de otro cliente (403 AccessDeniedException)")
    void obtenerPorId_otroCliente_lanzaAccessDenied() {
        Cliente propio = new Cliente();
        propio.setId(5L);
        Usuario usuario = usuarioCliente(propio);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(usuario);
        when(authenticatedUserService.esAdmin(usuario)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> servicio.obtenerPorId(99L));
        verify(clienteRepository, never()).findByIdAndActivoTrue(any());
    }

    @Test
    @DisplayName("ADMIN puede consultar cliente inexistente y recibe ResourceNotFoundException")
    void obtenerPorId_adminInexistente_lanzaExcepcion() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);
        when(clienteRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.obtenerPorId(99L));
    }

    @Test
    @DisplayName("Actualizacion parcial por su titular solo procesa los campos enviados")
    void actualizar_parcial_soloProcesaCamposEnviados() {
        Cliente cliente = new Cliente();
        cliente.setId(3L);
        cliente.setCorreo("juan.garcia@banco.com");
        Usuario usuario = usuarioCliente(cliente);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(usuario);
        when(authenticatedUserService.esAdmin(usuario)).thenReturn(false);

        when(clienteRepository.findByIdAndActivoTrue(3L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(cliente)).thenReturn(cliente);
        when(clienteMapper.toResponse(cliente)).thenReturn(new ClienteResponse());

        ClienteUpdateRequest request = new ClienteUpdateRequest();
        request.setNombre("Juan Carlos");

        servicio.actualizar(3L, request);

        verify(clienteMapper).updateEntity(request, cliente);
        verify(generosRepository, never()).findById(any());
        verify(coloniaRepository, never()).findById(any());
        verify(clienteRepository).save(cliente);
    }

    @Test
    @DisplayName("Actualizacion a menor de edad lanza IllegalArgumentException")
    void actualizar_menorDeEdad_lanzaExcepcion() {
        Cliente cliente = new Cliente();
        cliente.setId(3L);
        Usuario usuario = usuarioCliente(cliente);
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(usuario);
        when(authenticatedUserService.esAdmin(usuario)).thenReturn(false);
        when(clienteRepository.findByIdAndActivoTrue(3L)).thenReturn(Optional.of(cliente));

        ClienteUpdateRequest request = new ClienteUpdateRequest();
        request.setFechaNacimiento(LocalDate.now().minusYears(15));

        assertThrows(IllegalArgumentException.class, () -> servicio.actualizar(3L, request));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Baja logica por ADMIN inactiva cliente, usuario y su unica cuenta de ahorro")
    void darDeBaja_inactivaClienteUsuarioYCuentaUnica() {
        Usuario admin = usuarioAdmin();
        when(authenticatedUserService.obtenerUsuarioAutenticadoActivo()).thenReturn(admin);
        when(authenticatedUserService.esAdmin(admin)).thenReturn(true);

        Cliente cliente = new Cliente();
        cliente.setId(7L);
        cliente.setActivo(true);

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setActivo(true);
        cliente.setUsuario(usuario);

        Cuenta activa = new Cuenta();
        activa.setNumeroCuenta("1234567890");
        activa.setTipoCuenta(TipoCuenta.AHORRO);
        activa.setEstatus(EstatusCuenta.ACTIVA);
        cliente.setCuenta(activa);

        when(clienteRepository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(cliente));

        servicio.darDeBaja(7L);

        assertFalse(cliente.isActivo());
        assertFalse(usuario.isActivo());
        assertEquals(EstatusCuenta.INACTIVA, activa.getEstatus());
        verify(usuarioRepository).save(usuario);
        verify(cuentaRepository).save(activa);
    }

    private static Usuario usuarioCliente(Cliente cliente) {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setRol(AuthenticatedUserService.ROLE_CLIENTE);
        u.setActivo(true);
        u.setCliente(cliente);
        return u;
    }

    private static Usuario usuarioAdmin() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setCorreo("admin@banco.com");
        u.setRol(AuthenticatedUserService.ROLE_ADMIN);
        u.setActivo(true);
        return u;
    }

    private static ClienteRegistroRequest requestValido() {
        DomicilioDTO domicilio = new DomicilioDTO();
        domicilio.setCalle("Avenida Reforma");
        domicilio.setNoExterior("123");
        domicilio.setColoniaId(1L);
        domicilio.setMunicipioId(1L);
        domicilio.setEstadoId(1L);
        domicilio.setPaisId(1L);
        domicilio.setCp("01000");

        ClienteRegistroRequest request = new ClienteRegistroRequest();
        request.setNombre("Juan");
        request.setApellidoPaterno("Garcia");
        request.setApellidoMaterno("Lopez");
        request.setFechaNacimiento(LocalDate.now().minusYears(30));
        request.setCurp("GARC850101HDFRRS09");
        request.setRfc("GARR850101AB1");
        request.setGeneroId(1L);
        request.setNacionalidadId(1L);
        request.setEstadoCivilId(1L);
        request.setCorreo("juan.garcia@banco.com");
        request.setTelefonoMovil("5512345678");
        request.setOcupacion("Ingeniero");
        request.setEmpresa("Banco del Proyecto");
        request.setIngresoMensual(new BigDecimal("15000.00"));
        request.setDomicilio(domicilio);
        request.setSaldoInicial(new BigDecimal("1000.00"));
        request.setPassword("Banco123@");
        return request;
    }
}
