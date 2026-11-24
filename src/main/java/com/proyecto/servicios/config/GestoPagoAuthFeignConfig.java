package com.proyecto.servicios.config;

import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

public class GestoPagoAuthFeignConfig {

    @Bean
    public Logger.Level gestoPagoAuthLoggerLevel() {
        return Logger.Level.NONE;
    }

    @Bean
    public Retryer gestoPagoAuthNoRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
