package com.proyecto.servicios.exception;

public class ProveedorExternoException extends RuntimeException {

    public ProveedorExternoException(String message) {
        super(message);
    }

    public ProveedorExternoException(String message, Throwable cause) {
        super(message, cause);
    }
}

