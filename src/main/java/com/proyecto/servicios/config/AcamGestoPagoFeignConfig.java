package com.proyecto.servicios.config;

import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class AcamGestoPagoFeignConfig {

    @Bean
    public RequestInterceptor gestoPagoAuthInterceptor(
            GestoPagoTokenService tokenService,
            @Value("${gestopago.auth.api-key:}") String apiKey) {
        return requestTemplate -> {
            String token = tokenService.obtenerBearerTokenValido();
            requestTemplate.header("Authorization", "Bearer " + token);
            if (apiKey != null && !apiKey.isBlank()) {
                requestTemplate.header("X-API-Key", apiKey.trim());
            }
        };
    }

    @Bean
    public Logger.Level gestoPagoFeignLoggerLevel() {
        return Logger.Level.NONE;
    }
}
