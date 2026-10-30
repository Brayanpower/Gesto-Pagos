package com.proyecto.servicios.config;

import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class GestoPagoSendTxFeignConfig {

    @Bean
    public RequestInterceptor gestoPagoSendTxAuthInterceptor(GestoPagoTokenService tokenService) {
        return requestTemplate -> {
            String token = tokenService.obtenerBearerTokenValido();
            requestTemplate.header("Authorization", "Bearer " + token);
        };
    }

    /**
     * Prohibido reintentar operaciones financieras automaticamente ante timeouts:
     * un timeout de lectura no garantiza que el proveedor no haya procesado el cargo.
     */
    @Bean
    public Retryer gestoPagoNoRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Request.Options gestoPagoSendTxOptions(
            @Value("${gestopago.sendtx.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${gestopago.sendtx.read-timeout-ms:15000}") long readTimeoutMs) {
        return new Request.Options(
                connectTimeoutMs, TimeUnit.MILLISECONDS,
                readTimeoutMs, TimeUnit.MILLISECONDS,
                false
        );
    }

    @Bean
    public Logger.Level gestoPagoSendTxLoggerLevel() {
        return Logger.Level.NONE;
    }
}

