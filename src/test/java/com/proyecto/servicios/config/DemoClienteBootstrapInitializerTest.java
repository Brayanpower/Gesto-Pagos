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
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de DemoClienteBootstrapInitializer")
class DemoClienteBootstrapInitializerTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private DomicilioClienteRepository domicilioClienteRepository;
    @Mock private CuentaRepository cuentaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EntityManager entityManager;

    private DemoClienteBootstrapInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DemoClienteBootstrapInitializer(
                usuarioRepository,
                clienteRepository,
                domicilioClienteRepository,
                cuentaRepository,
                passwordEncoder,
                entityManager
        );
        ReflectionTestUtils.setField(initializer, "clienteCorreo", "Cliente@Banco.com");
        ReflectionTestUtils.setField(initializer, "clientePassword", "Cliente123@");
        ReflectionTestUtils.setField(initializer, "saldoInicial", new BigDecimal("5000.00"));
        ReflectionTestUtils.setField(initializer, "adminCorreo", "admin@banco.com");
    }

    @Test
    @DisplayName("Crea cliente, domicilio, cuenta de ahorro ACTIVA y usuario ROLE_CLIENTE cuando no existen")
    void inicializarClienteDemo_noExiste_creaTodoElExpediente() {
        when(usuarioRepository.existsByCorreo("cliente@banco.com")).thenReturn(false);
        when(clienteRepository.existsByCorreo("cliente@banco.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta("4000100001")).thenReturn(false);
        when(passwordEncoder.encode("Cliente123@")).thenReturn("$2a$10$encoded");

        mockCatalogo(CatalogoGeneros.class, List.of(new CatalogoGeneros(1L, "Masculino")));
        mockCatalogo(CatalogoNacionalidad.class, List.of(new CatalogoNacionalidad(1L, "Mexicana")));
        mockCatalogo(CatalogoEstadoCivil.class, List.of(new CatalogoEstadoCivil(1L, "Soltero")));

        CatalogoPais pais = new CatalogoPais(1L, "Mexico");
        CatalogoEstado estado = new CatalogoEstado(1L, "Ciudad de Mexico", pais);
        CatalogoMunicipio municipio = new CatalogoMunicipio(1L, "Cuauhtemoc", estado);
        CatalogoColonia colonia = new CatalogoColonia(1L, "Centro", municipio);
        mockCatalogo(CatalogoColonia.class, List.of(colonia));

        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(domicilioClienteRepository.save(any(DomicilioCliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.inicializarClienteDemoSiNoExiste();

        ArgumentCaptor<Cuenta> cuentaCaptor = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).save(cuentaCaptor.capture());
        Cuenta cuenta = cuentaCaptor.getValue();
        assertEquals("4000100001", cuenta.getNumeroCuenta());
        assertEquals(TipoCuenta.AHORRO, cuenta.getTipoCuenta());
        assertEquals(EstatusCuenta.ACTIVA, cuenta.getEstatus());
        assertEquals(new BigDecimal("5000.00"), cuenta.getSaldo());
        assertEquals("admin@banco.com", cuenta.getRevisadoPor());
        assertNotNull(cuenta.getFechaRevision());

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuario = usuarioCaptor.getValue();
        assertEquals("cliente@banco.com", usuario.getCorreo());
        assertEquals("$2a$10$encoded", usuario.getPasswordHash());
        assertEquals(AuthenticatedUserService.ROLE_CLIENTE, usuario.getRol());
        assertTrue(usuario.isActivo());
        assertTrue(usuario.isTotpObligatorio());
    }

    @Test
    @DisplayName("Es idempotente cuando el correo ya existe")
    void inicializarClienteDemo_yaExiste_noDuplica() {
        when(usuarioRepository.existsByCorreo("cliente@banco.com")).thenReturn(true);

        initializer.inicializarClienteDemoSiNoExiste();

        verify(clienteRepository, never()).save(any());
        verify(cuentaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Omite creacion sin fallar cuando no hay catalogos cargados")
    void inicializarClienteDemo_sinCatalogos_noFalla() {
        when(usuarioRepository.existsByCorreo("cliente@banco.com")).thenReturn(false);
        when(clienteRepository.existsByCorreo("cliente@banco.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(clienteRepository.existsByRfc(anyString())).thenReturn(false);

        mockCatalogo(CatalogoGeneros.class, List.of());
        mockCatalogo(CatalogoNacionalidad.class, List.of());
        mockCatalogo(CatalogoEstadoCivil.class, List.of());
        mockCatalogo(CatalogoColonia.class, List.of());

        initializer.inicializarClienteDemoSiNoExiste();

        verify(clienteRepository, never()).save(any());
        verify(cuentaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private <T> void mockCatalogo(Class<T> clazz, List<T> resultado) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(clazz))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(resultado);
    }
}

