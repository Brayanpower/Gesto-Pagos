package com.proyecto.servicios.model.seguridad;

public record ClientMetadata(String ipOrigen, String userAgent) {

    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    public ClientMetadata {
        ipOrigen = normalizar(ipOrigen, MAX_IP_LENGTH);
        userAgent = normalizar(userAgent, MAX_USER_AGENT_LENGTH);
    }

    public static ClientMetadata desconocido() {
        return new ClientMetadata(null, null);
    }

    public static ClientMetadata of(String ip, String ua) {
        return new ClientMetadata(ip, ua);
    }

    private static String normalizar(String valor, int maxLength) {
        if (valor == null) {
            return null;
        }
        String trimmed = valor.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}

