package com.proyecto.servicios.exception;

public class CatalogoNotFoundException extends RuntimeException {

    public CatalogoNotFoundException(String mensaje) {
        super(mensaje);
    }
}
