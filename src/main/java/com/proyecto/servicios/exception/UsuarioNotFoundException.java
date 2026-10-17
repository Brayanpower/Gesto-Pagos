package com.proyecto.servicios.exception;

public class UsuarioNotFoundException extends RuntimeException {

    public UsuarioNotFoundException(String mensaje) {
        super(mensaje);
    }

    public UsuarioNotFoundException(Long id) {
        this("Usuario no encontrado con ID: " + id);
    }
}
