package com.proyecto.servicios.security;

import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern SECRET_PARAM_PATTERN = Pattern.compile("(?i)(password|token|secret|key|iv|signed)=([^&\\s\"',;]+)");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile("(?i)(Authorization\\s*[:=]\\s*)([^\\r\\n,]+)");

    private LogSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String sanitized = BEARER_PATTERN.matcher(input).replaceAll("$1[REDACTED]");
        sanitized = SECRET_PARAM_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = AUTH_HEADER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        return sanitized;
    }

    public static String maskAccount(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.length() <= 4) {
            return "****";
        }
        return "****" + numeroCuenta.substring(numeroCuenta.length() - 4);
    }
}

