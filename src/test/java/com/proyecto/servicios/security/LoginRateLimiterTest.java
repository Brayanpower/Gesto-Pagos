package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas unitarias de LoginRateLimiter")
class LoginRateLimiterTest {

    @Test
    @DisplayName("Permite intentos hasta el limite y bloquea al exceder con Retry-After")
    void verificarYRegistrarIntento_permiteHastaLimiteYBloqueaAlExceder() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(3, 60L, clock);

        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("192.168.1.10", "Cliente@Banco.com"));
        clock.advanceSeconds(10);
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("192.168.1.10", "cliente@banco.com"));
        clock.advanceSeconds(10);
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("192.168.1.10", " CLIENTE@BANCO.COM "));

        clock.advanceSeconds(5);
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> limiter.verificarYRegistrarIntento("192.168.1.10", "cliente@banco.com"));

        assertEquals(35L, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Limita independientemente por IP y por correo normalizado")
    void verificarYRegistrarIntento_limitaPorIpYPorCorreo() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(2, 60L, clock);

        limiter.verificarYRegistrarIntento("10.0.0.1", "uno@banco.com");
        limiter.verificarYRegistrarIntento("10.0.0.2", "uno@banco.com");

        // El tercer intento al mismo correo desde una IP nueva debe ser bloqueado sin quemar la IP nueva
        RateLimitExceededException mailBlocked = assertThrows(RateLimitExceededException.class,
                () -> limiter.verificarYRegistrarIntento("10.0.0.3", "UNO@banco.com"));
        assertTrue(mailBlocked.getRetryAfterSeconds() >= 1L);

        // La IP 10.0.0.3 no debio consumir intentos al ser rechazada por la clave del correo
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("10.0.0.3", "otro1@banco.com"));
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("10.0.0.3", "otro2@banco.com"));

        assertThrows(RateLimitExceededException.class,
                () -> limiter.verificarYRegistrarIntento("10.0.0.3", "otro3@banco.com"));
    }

    @Test
    @DisplayName("Respeta la ventana deslizante y vuelve a permitir intentos tras expirar")
    void verificarYRegistrarIntento_respetaVentanaDeslizante() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(2, 30L, clock);

        limiter.verificarYRegistrarIntento("200.1.2.3", "usuario@banco.com");
        clock.advanceSeconds(15);
        limiter.verificarYRegistrarIntento("200.1.2.3", "usuario@banco.com");

        RateLimitExceededException blocked = assertThrows(RateLimitExceededException.class,
                () -> limiter.verificarYRegistrarIntento("200.1.2.3", "usuario@banco.com"));
        assertEquals(15L, blocked.getRetryAfterSeconds());

        // A los 30s exactos del primer intento, ese primer intento expira y se habilita 1 espacio
        clock.advanceSeconds(15);
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento("200.1.2.3", "usuario@banco.com"));

        // El siguiente vuelve a exceder hasta que pasen 15s mas
        RateLimitExceededException blockedAgain = assertThrows(RateLimitExceededException.class,
                () -> limiter.verificarYRegistrarIntento("200.1.2.3", "usuario@banco.com"));
        assertEquals(15L, blockedAgain.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Ignora claves nulas o en blanco sin fallar")
    void verificarYRegistrarIntento_nulosOBlancos_noLanzaError() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 60L);
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento(null, "   "));
        assertDoesNotThrow(() -> limiter.verificarYRegistrarIntento(" ", null));
    }

    @Test
    @DisplayName("Es seguro bajo concurrencia y no permite superar maxAttempts")
    void verificarYRegistrarIntento_concurrencia_respetaLimiteExacto() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        int limite = 10;
        int totalHilos = 30;
        LoginRateLimiter limiter = new LoginRateLimiter(limite, 60L, clock);

        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch listo = new CountDownLatch(1);
        List<Callable<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < totalHilos; i++) {
            tareas.add(() -> {
                listo.await();
                try {
                    limiter.verificarYRegistrarIntento("172.16.0.5", "concurrente@banco.com");
                    return true;
                } catch (RateLimitExceededException ex) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> resultados = new ArrayList<>();
        for (Callable<Boolean> tarea : tareas) {
            resultados.add(pool.submit(tarea));
        }
        listo.countDown();

        int permitidos = 0;
        int bloqueados = 0;
        for (Future<Boolean> future : resultados) {
            if (Boolean.TRUE.equals(future.get())) {
                permitidos++;
            } else {
                bloqueados++;
            }
        }
        pool.shutdownNow();

        assertEquals(limite, permitidos);
        assertEquals(totalHilos - limite, bloqueados);
    }

    @Test
    @DisplayName("Spring inyecta propiedades de rate limit en constructor por defecto")
    void springContext_inyectaPropiedades() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rl-props", Map.of(
                    "app.auth.rate-limit.max-attempts", "2",
                    "app.auth.rate-limit.window-seconds", "45"
            )));
            ctx.register(LoginRateLimiter.class);
            ctx.refresh();

            LoginRateLimiter bean = ctx.getBean(LoginRateLimiter.class);
            bean.verificarYRegistrarIntento("1.1.1.1", "a@b.com");
            bean.verificarYRegistrarIntento("1.1.1.1", "a@b.com");
            RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                    () -> bean.verificarYRegistrarIntento("1.1.1.1", "a@b.com"));
            assertTrue(ex.getRetryAfterSeconds() >= 1L && ex.getRetryAfterSeconds() <= 45L);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advanceSeconds(long seconds) {
            current.updateAndGet(inst -> inst.plusSeconds(seconds));
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
            return current.get();
        }
    }
}
