package com.proyecto.servicios.config;

import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class acamGestoPagoFeignConfig {

    @Bean
    public RequestInterceptor gestoPagoAuthInterceptor(GestoPagoTokenService tokenService) {
        return requestTemplate -> {
            String token = tokenService.obtenerBearerTokenValido();
            requestTemplate.header("Authorization", "Bearer " + token);
        };
    }

    @Bean
    public Logger.Level gestoPagoFeignLoggerLevel() {
        return Logger.Level.NONE;
    }
}
