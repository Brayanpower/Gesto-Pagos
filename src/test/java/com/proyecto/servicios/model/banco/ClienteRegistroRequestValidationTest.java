package com.proyecto.servicios.model.banco;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validaciones de ClienteRegistroRequest")
class ClienteRegistroRequestValidationTest {

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
    @DisplayName("Un request completo y valido no produce violaciones")
    void requestValido_sinViolaciones() {
        assertTrue(validator.validate(requestValido()).isEmpty());
    }

    @Test
    @DisplayName("CURP con formato invalido produce violacion")
    void curpInvalida_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setCurp("12345");
        assertTrue(propiedadesConError(request).contains("curp"));
    }

    @Test
    @DisplayName("RFC con formato invalido produce violacion")
    void rfcInvalido_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setRfc("XXXX");
        assertTrue(propiedadesConError(request).contains("rfc"));
    }

    @Test
    @DisplayName("Telefono movil que no tiene 10 digitos produce violacion")
    void telefonoInvalido_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setTelefonoMovil("12345");
        assertTrue(propiedadesConError(request).contains("telefonoMovil"));
    }

    @Test
    @DisplayName("Correo con formato invalido produce violacion")
    void correoInvalido_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setCorreo("correo-sin-arroba");
        assertTrue(propiedadesConError(request).contains("correo"));
    }

    @Test
    @DisplayName("Contrasena sin mayuscula produce violacion")
    void passwordSinMayuscula_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setPassword("abcdef1@");
        assertTrue(propiedadesConError(request).contains("password"));
    }

    @Test
    @DisplayName("Contrasena sin caracter especial produce violacion")
    void passwordSinEspecial_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setPassword("Abcdef12");
        assertTrue(propiedadesConError(request).contains("password"));
    }

    @Test
    @DisplayName("Ingreso mensual igual a cero produce violacion")
    void ingresoCero_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setIngresoMensual(BigDecimal.ZERO);
        assertTrue(propiedadesConError(request).contains("ingresoMensual"));
    }

    @Test
    @DisplayName("Saldo inicial negativo produce violacion")
    void saldoInicialNegativo_produceViolacion() {
        ClienteRegistroRequest request = requestValido();
        request.setSaldoInicial(new BigDecimal("-1.00"));
        assertTrue(propiedadesConError(request).contains("saldoInicial"));
    }

    @Test
    @DisplayName("CURP y RFC en minusculas son aceptados para su posterior normalizacion")
    void curpRfcMinusculas_sonAceptados() {
        ClienteRegistroRequest request = requestValido();
        request.setCurp("garc850101hdfrrs09");
        request.setRfc("garr850101ab1");
        Set<String> errores = propiedadesConError(request);
        assertFalse(errores.contains("curp"));
        assertFalse(errores.contains("rfc"));
    }

    private Set<String> propiedadesConError(ClienteRegistroRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
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
