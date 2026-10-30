package com.proyecto.servicios.model.seguridad;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validaciones de los DTOs del modulo de usuarios")
class UsuarioRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("UsuarioCreacionRequest valido no produce violaciones")
    void creacionValida_sinViolaciones() {
        assertTrue(validator.validate(creacionValida()).isEmpty());
    }

    @Test
    @DisplayName("UsuarioCreacionRequest sin clienteId produce violacion en clienteId")
    void creacionSinClienteId_produceViolacion() {
        UsuarioCreacionRequest request = creacionValida();
        request.setClienteId(null);
        assertTrue(propiedadesConError(request).contains("clienteId"));
    }

    @Test
    @DisplayName("UsuarioCreacionRequest con correo invalido produce violacion en correo")
    void creacionCorreoInvalido_produceViolacion() {
        UsuarioCreacionRequest request = creacionValida();
        request.setCorreo("correo-sin-arroba");
        assertTrue(propiedadesConError(request).contains("correo"));
    }

    @Test
    @DisplayName("UsuarioCreacionRequest con password debil produce violacion en password")
    void creacionPasswordDebil_produceViolacion() {
        UsuarioCreacionRequest request = creacionValida();
        request.setPassword("abcdef");
        assertTrue(propiedadesConError(request).contains("password"));
    }

    @Test
    @DisplayName("UsuarioCreacionRequest con rol fuera del whitelist produce violacion en rol")
    void creacionRolInvalido_produceViolacion() {
        UsuarioCreacionRequest request = creacionValida();
        request.setRol("ROLE_ROOT");
        assertTrue(propiedadesConError(request).contains("rol"));
    }

    @Test
    @DisplayName("UsuarioActualizacionRequest sin id produce violacion en id")
    void actualizacionSinId_produceViolacion() {
        UsuarioActualizacionRequest request = new UsuarioActualizacionRequest();
        request.setCorreo("cliente@banco.com");
        assertTrue(propiedadesConError(request).contains("id"));
    }

    @Test
    @DisplayName("UsuarioActualizacionRequest con rol invalido produce violacion en rol")
    void actualizacionRolInvalido_produceViolacion() {
        UsuarioActualizacionRequest request = new UsuarioActualizacionRequest();
        request.setId(1L);
        request.setRol("SUPER_ADMIN");
        assertTrue(propiedadesConError(request).contains("rol"));
    }

    @Test
    @DisplayName("UsuarioBusquedaRequest con correo invalido produce violacion en correo")
    void busquedaCorreoInvalido_produceViolacion() {
        UsuarioBusquedaRequest request = new UsuarioBusquedaRequest();
        request.setCorreo("no-es-un-correo");
        assertTrue(propiedadesConError(request).contains("correo"));
    }

    @Test
    @DisplayName("UsuarioBusquedaRequest totalmente vacia no produce violaciones (filtros opcionales)")
    void busquedaVacia_sinViolaciones() {
        assertTrue(validator.validate(new UsuarioBusquedaRequest()).isEmpty());
    }

    @Test
    @DisplayName("UsuarioEstadoRequest sin estado produce violacion en activo")
    void estadoSinActivo_produceViolacion() {
        UsuarioEstadoRequest request = new UsuarioEstadoRequest();
        request.setId(1L);
        assertTrue(propiedadesConError(request).contains("activo"));
    }

    @Test
    @DisplayName("UsuarioEstadoRequest sin id produce violacion en id")
    void estadoSinId_produceViolacion() {
        UsuarioEstadoRequest request = new UsuarioEstadoRequest();
        request.setActivo(true);
        assertTrue(propiedadesConError(request).contains("id"));
    }

    private UsuarioCreacionRequest creacionValida() {
        UsuarioCreacionRequest request = new UsuarioCreacionRequest();
        request.setClienteId(1L);
        request.setCorreo("cliente@banco.com");
        request.setPassword("Banco123@");
        request.setRol("ROLE_CLIENTE");
        return request;
    }

    private <T> Set<String> propiedadesConError(T request) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(request);
        assertFalse(violaciones.isEmpty(), "Se esperaban violaciones de validacion");
        return violaciones.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
