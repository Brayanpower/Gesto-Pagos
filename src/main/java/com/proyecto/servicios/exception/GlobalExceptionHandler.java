package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.ErrorDetailResponse;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetailResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Error de validacion en la peticion: {}", ex.getMessage());
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        ErrorDetailResponse response = new ErrorDetailResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Error en la validacion de los campos enviados",
                errores
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDetailResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Violacion de restricciones de validacion: {}", ex.getMessage());
        Map<String, String> errores = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errores.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        ErrorDetailResponse response = new ErrorDetailResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Parametros invalidos en la solicitud",
                errores
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<GenericResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.NOT_FOUND.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CatalogoNotFoundException.class)
    public ResponseEntity<GenericResponse> handleCatalogoNotFound(CatalogoNotFoundException ex) {
        log.warn("Catalogo no encontrado: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.NOT_FOUND.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<GenericResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Recurso duplicado o conflicto: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.CONFLICT.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violacion de restriccion de integridad (unicidad o llave foranea): {}", ex.getMostSpecificCause().getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.CONFLICT.value());
        response.setMensaje("La operacion viola una restriccion de unicidad o integridad de datos. Verifique CURP, RFC, correo o numero de cuenta.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenericResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Regla de negocio o argumento invalido: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<GenericResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Credenciales invalidas: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNAUTHORIZED.value());
        response.setMensaje("Credenciales invalidas: Correo o contrasena incorrectos");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<GenericResponse> handleDisabledException(DisabledException ex) {
        log.warn("Usuario inactivo: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.FORBIDDEN.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.FORBIDDEN.value());
        response.setMensaje("Acceso denegado: No cuenta con permisos para este recurso");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<GenericResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Error de autenticacion: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.UNAUTHORIZED.value());
        response.setMensaje("Autenticacion requerida: " + ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la solicitud invalido o malformado: {}", ex.getMessage());
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_REQUEST.value());
        response.setMensaje("El formato del JSON o los tipos de datos enviados son invalidos");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(GestoPagoCatProductException.class)
    public ResponseEntity<GenericResponse> handleGestoPagoCatProduct(GestoPagoCatProductException ex) {
        log.error("Error controlado al obtener catalogo de GestoPago: {}", ex.getMessage(), ex);
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.BAD_GATEWAY.value());
        response.setMensaje(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGlobalException(Exception ex) {
        log.error("Error no controlado en el servidor: {}", ex.getMessage(), ex);
        GenericResponse response = new GenericResponse();
        response.setCodigo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setMensaje("Ocurrio un error interno en el servidor bancario.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}