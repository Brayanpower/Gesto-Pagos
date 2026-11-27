package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas unitarias de PublicEndpointRateLimiter y ClientIpResolver")
class PublicEndpointRateLimiterTest {

    @Test
    @DisplayName("PublicEndpointRateLimiter limita onboarding e imagenes de forma independiente por IP y se recupera al expirar ventana")
    void rateLimiter_limitaYSeRecuperaTrasVentana() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-24T00:00:00Z"));
        PublicEndpointRateLimiter limiter = new PublicEndpointRateLimiter(2, 60L, 3, 30L, clock);

        limiter.verificarLimiteOnboarding("198.51.100.1");
        limiter.verificarLimiteOnboarding("198.51.100.1");

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.verificarLimiteOnboarding("198.51.100.1"));
        assertTrue(ex.getRetryAfterSeconds() > 0L);

        // Otra IP y endpoint de imagenes conservan su propio presupuesto
        assertDoesNotThrow(() -> limiter.verificarLimiteOnboarding("198.51.100.2"));
        assertDoesNotThrow(() -> limiter.verificarLimiteImagenes("198.51.100.1"));
        assertDoesNotThrow(() -> limiter.verificarLimiteImagenes("198.51.100.1"));
        assertDoesNotThrow(() -> limiter.verificarLimiteImagenes("198.51.100.1"));
        assertThrows(RateLimitExceededException.class, () -> limiter.verificarLimiteImagenes("198.51.100.1"));

        clock.advanceSeconds(31L);
        assertDoesNotThrow(() -> limiter.verificarLimiteImagenes("198.51.100.1"));
        assertThrows(RateLimitExceededException.class, () -> limiter.verificarLimiteOnboarding("198.51.100.1"));

        clock.advanceSeconds(30L);
        assertDoesNotThrow(() -> limiter.verificarLimiteOnboarding("198.51.100.1"));
    }

    @Test
    @DisplayName("ClientIpResolver ignora X-Forwarded-For cuando trustForwardedHeaders=false y sanitiza cuando es true")
    void clientIpResolver_respetaConfianzaYSanitiza() {
        assertNull(ClientIpResolver.resolverIpOrigen(null, true));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.99\r\n, 10.0.0.1");

        assertEquals("10.10.0.5", ClientIpResolver.resolverIpOrigen(request, false));
        assertEquals("198.51.100.99", ClientIpResolver.resolverIpOrigen(request, true));

        MockHttpServletRequest longIpRequest = new MockHttpServletRequest();
        longIpRequest.addHeader("X-Forwarded-For", "a".repeat(80));
        assertEquals(64, ClientIpResolver.resolverIpOrigen(longIpRequest, true).length());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
