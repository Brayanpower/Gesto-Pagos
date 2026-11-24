package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Pruebas unitarias de GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BadCredentialsException preserva mensajes especificos de TOTP/Refresh y estandariza login")
    void handleBadCredentials_preservaMensajesEspecificos() {
        ResponseEntity<GenericResponse> loginGenerico = handler.handleBadCredentials(
                new BadCredentialsException("Credenciales invalidas"));
        assertEquals(HttpStatus.UNAUTHORIZED, loginGenerico.getStatusCode());
        assertNotNull(loginGenerico.getBody());
        assertEquals("Credenciales invalidas: Correo o contrasena incorrectos", loginGenerico.getBody().getMensaje());

        ResponseEntity<GenericResponse> totpInvalido = handler.handleBadCredentials(
                new BadCredentialsException("Codigo de verificacion TOTP invalido"));
        assertNotNull(totpInvalido.getBody());
        assertEquals("Codigo de verificacion TOTP invalido", totpInvalido.getBody().getMensaje());
    }

    @Test
    @DisplayName("Maneja correctamente ReglaNegocioException, DuplicateResourceException, AccessDeniedException y Exception")
    void handlersPrincipales_retornanCodigosHttpEsperados() {
        ResponseEntity<GenericResponse> regla = handler.handleReglaNegocio(new ReglaNegocioException("Regla invalida"));
        assertEquals(HttpStatus.BAD_REQUEST, regla.getStatusCode());
        assertEquals(400, regla.getBody().getCodigo());

        ResponseEntity<GenericResponse> dup = handler.handleDuplicateResource(new DuplicateResourceException("Duplicado"));
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());
        assertEquals(409, dup.getBody().getCodigo());

        ResponseEntity<GenericResponse> denied = handler.handleAccessDenied(new AccessDeniedException("Sin permiso"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals(403, denied.getBody().getCodigo());

        ResponseEntity<GenericResponse> internal = handler.handleGlobalException(new RuntimeException("Fallo inesperado"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, internal.getStatusCode());
        assertEquals(500, internal.getBody().getCodigo());
    }
}
