package com.proyecto.servicios.model.gestopago;

import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validaciones de SendTxRequest")
class SendTxRequestValidationTest {

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
    @DisplayName("Solicitud valida con campos requeridos no genera violaciones")
    void requestValido_sinViolaciones() {
        assertTrue(validator.validate(requestBase()).isEmpty());
    }

    @Test
    @DisplayName("Numero de cuenta vacio o con letras genera violacion")
    void numeroCuentaInvalido_generaViolacion() {
        SendTxRequest request = requestBase();
        request.setNumeroCuenta("ABC12345");
        assertTrue(camposConError(request).contains("numeroCuenta"));
    }

    @Test
    @DisplayName("Monto menor a 1.00 o con mas de 2 decimales genera violacion")
    void montoInvalido_generaViolacion() {
        SendTxRequest montoCero = requestBase();
        montoCero.setMontoPago(BigDecimal.ZERO);
        assertTrue(camposConError(montoCero).contains("montoPago"));

        SendTxRequest tresDecimales = requestBase();
        tresDecimales.setMontoPago(new BigDecimal("100.123"));
        assertTrue(camposConError(tresDecimales).contains("montoPago"));
    }

    @Test
    @DisplayName("IDs de servicio o producto negativos generan violacion")
    void idsNegativos_generanViolacion() {
        SendTxRequest request = requestBase();
        request.setIdServicio(-1);
        request.setIdProducto(0);
        Set<String> errores = camposConError(request);
        assertTrue(errores.contains("idServicio"));
        assertTrue(errores.contains("idProducto"));
    }

    @Test
    @DisplayName("Referencia con caracteres especiales no permitidos genera violacion")
    void referenciaConCaracteresPeligrosos_generaViolacion() {
        SendTxRequest request = requestBase();
        request.setReferencia("REF<script>alert(1)</script>");
        assertTrue(camposConError(request).contains("referencia"));
    }

    private Set<String> camposConError(SendTxRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static SendTxRequest requestBase() {
        return SendTxRequest.builder()
                .numeroCuenta("1234567890")
                .idServicio(2284)
                .idProducto(14302)
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .referencia("REF123456")
                .claveIdempotencia("idem-key-12345")
                .build();
    }
}

