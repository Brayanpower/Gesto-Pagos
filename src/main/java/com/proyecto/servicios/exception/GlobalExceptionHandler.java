package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.ErrorDetailResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.security.LogSanitizer;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetailResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Error de validacion en campos solicitados: {}", errores.keySet());
        ErrorDetailResponse response = new ErrorDetailResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Error en la validacion de los campos enviados",
                errores
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDetailResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errores.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        log.warn("Violacion de restricciones en parametros: {}", errores.keySet());
        ErrorDetailResponse response = new ErrorDetailResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Parametros invalidos en la solicitud",
                errores
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Tipo de parametro invalido en solicitud: {}", ex.getName());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje("El formato del parametro '" + ex.getName() + "' no es valido");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Parametro requerido faltante: {}", ex.getParameterName());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje("El parametro requerido '" + ex.getParameterName() + "' es obligatorio");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<GenericResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.NOT_FOUND.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CatalogoNotFoundException.class)
    public ResponseEntity<GenericResponse> handleCatalogoNotFound(CatalogoNotFoundException ex) {
        log.warn("Catalogo no encontrado: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.NOT_FOUND.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<GenericResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Recurso duplicado o conflicto de idempotencia: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.CONFLICT.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<GenericResponse> handlePessimisticLocking(PessimisticLockingFailureException ex) {
        log.warn("Conflicto de concurrencia al bloquear cuenta bancaria");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.CONFLICT.value());
        response.setMensaje("La cuenta tiene otra operacion en curso en este momento. Intente nuevamente en unos segundos.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UsuarioNotFoundException.class)
    public ResponseEntity<GenericResponse> handleUsuarioNotFound(UsuarioNotFoundException ex) {
        log.warn("Usuario inexistente: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.NOT_FOUND.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<GenericResponse> handleReglaNegocio(ReglaNegocioException ex) {
        log.warn("Regla de negocio violada: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<GenericResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        log.warn("Operacion rechazada por saldo insuficiente: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(CuentaNoOperativaException.class)
    public ResponseEntity<GenericResponse> handleCuentaNoOperativa(CuentaNoOperativaException ex) {
        log.warn("Operacion rechazada por estado de cuenta: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(OperacionRechazadaProveedorException.class)
    public ResponseEntity<GenericResponse> handleOperacionRechazadaProveedor(OperacionRechazadaProveedorException ex) {
        log.warn("Operacion rechazada por el proveedor externo: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violacion de restriccion de integridad en base de datos");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.CONFLICT.value());
        response.setMensaje("La operacion viola una restriccion de unicidad o integridad de datos.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenericResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento invalido: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<GenericResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Intento de autenticacion con credenciales invalidas");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNAUTHORIZED.value());
        response.setMensaje("Credenciales invalidas: Correo o contrasena incorrectos");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<GenericResponse> handleDisabledException(DisabledException ex) {
        log.warn("Acceso rechazado para usuario inactivo");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.FORBIDDEN.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado a recurso protegido: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.FORBIDDEN.value());
        response.setMensaje("Acceso denegado: No cuenta con autorizacion para ejecutar esta operacion");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<GenericResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Fallo de autenticacion en solicitud entrante");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNAUTHORIZED.value());
        response.setMensaje("Autenticacion requerida o token invalido");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo JSON malformado o tipos de datos incompatibles");
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje("El formato del JSON o los tipos de datos enviados son invalidos");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(GestoPagoCatProductException.class)
    public ResponseEntity<GenericResponse> handleGestoPagoCatProduct(GestoPagoCatProductException ex) {
        log.error("Error controlado al consultar catalogo del proveedor: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_GATEWAY.value());
        response.setMensaje("El servicio de catalogo del proveedor no se encuentra disponible temporalmente.");
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(ProveedorExternoException.class)
    public ResponseEntity<GenericResponse> handleProveedorExterno(ProveedorExternoException ex) {
        log.error("Error de comunicacion previa con proveedor externo: {}", LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_GATEWAY.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(TransaccionTimeoutConciliacionException.class)
    public ResponseEntity<ErrorDetailResponse> handleTransaccionTimeoutConciliacion(
            TransaccionTimeoutConciliacionException ex) {
        log.error("Timeout en proveedor externo; transaccion en conciliacion con folio upc={}", ex.getUpc());
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("upc", ex.getUpc());
        detalles.put("estatus", ex.getEstatus().name());
        detalles.put("accionRecomendada", "Consulte el estatus con el folio UPC antes de intentar una nueva operacion");
        ErrorDetailResponse response = new ErrorDetailResponse(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                ex.getMessage(),
                detalles
        );
        return new ResponseEntity<>(response, HttpStatus.GATEWAY_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGlobalException(Exception ex) {
        log.error("Error interno no controlado [{}]: {}",
                ex.getClass().getSimpleName(), LogSanitizer.sanitize(ex.getMessage()));
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setMensaje("Ocurrio un error interno en el servidor bancario.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
