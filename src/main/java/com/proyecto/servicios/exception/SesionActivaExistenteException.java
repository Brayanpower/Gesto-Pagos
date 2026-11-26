package com.proyecto.servicios.exception;

import java.time.LocalDateTime;

/**
 * Se lanza cuando el usuario ya mantiene una sesion activa vigente en otro dispositivo
 * y no ha confirmado explícitamente el cierre de la sesion anterior.
 */
public class SesionActivaExistenteException extends RuntimeException {

    private final String ipOrigenActiva;
    private final String userAgentActivo;
    private final LocalDateTime fechaInicioSesionActiva;

    public SesionActivaExistenteException(
            String message,
            String ipOrigenActiva,
            String userAgentActivo,
            LocalDateTime fechaInicioSesionActiva) {
        super(message);
        this.ipOrigenActiva = ipOrigenActiva;
        this.userAgentActivo = userAgentActivo;
        this.fechaInicioSesionActiva = fechaInicioSesionActiva;
    }

    public String getIpOrigenActiva() {
        return ipOrigenActiva;
    }

    public String getUserAgentActivo() {
        return userAgentActivo;
    }

    public LocalDateTime getFechaInicioSesionActiva() {
        return fechaInicioSesionActiva;
    }
}
