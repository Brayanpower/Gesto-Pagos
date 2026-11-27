package com.proyecto.servicios.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utilidad centralizada para extraer y sanitizar la IP origen del cliente desde {@link HttpServletRequest}.
 * Solo considera {@code X-Forwarded-For} cuando el despliegue declara proxies confiables.
 */
public final class ClientIpResolver {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_IP_LENGTH = 64;

    private ClientIpResolver() {
    }

    public static String resolverIpOrigen(HttpServletRequest request, boolean trustForwardedHeaders) {
        if (request == null) {
            return null;
        }
        String ipCandidata = null;
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                int commaIndex = forwardedFor.indexOf(',');
                String primerIp = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
                if (!primerIp.isBlank()) {
                    ipCandidata = primerIp;
                }
            }
        }
        if (ipCandidata == null || ipCandidata.isBlank()) {
            ipCandidata = request.getRemoteAddr();
        }
        return sanitizarIp(ipCandidata);
    }

    private static String sanitizarIp(String ip) {
        if (ip == null) {
            return null;
        }
        String limpia = ip.replace("\r", "").replace("\n", "").trim();
        if (limpia.isEmpty()) {
            return null;
        }
        return limpia.length() > MAX_IP_LENGTH ? limpia.substring(0, MAX_IP_LENGTH) : limpia;
    }
}
