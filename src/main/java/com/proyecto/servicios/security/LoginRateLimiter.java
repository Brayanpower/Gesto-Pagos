package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginRateLimiter {

    private static final int DEFAULT_MAX_ATTEMPTS = 15;
    private static final long DEFAULT_WINDOW_SECONDS = 60L;
    private static final int CLEANUP_EVERY_OPERATIONS = 64;
    private static final int MAX_KEYS_TRACKED = 10_000;
    private static final int MAX_CLEANUP_SCAN = 256;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final String PREFIX_IP = "ip:";
    private static final String PREFIX_MAIL = "mail:";

    private final ConcurrentHashMap<String, Deque<Instant>> intentosPorClave = new ConcurrentHashMap<>();
    private final AtomicInteger contadorOperaciones = new AtomicInteger(0);
    private final Clock clock;

    @Value("${app.auth.rate-limit.max-attempts:15}")
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @Value("${app.auth.rate-limit.window-seconds:60}")
    private long windowSeconds = DEFAULT_WINDOW_SECONDS;

    @Autowired
    public LoginRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_SECONDS, Clock.systemUTC());
    }

    public LoginRateLimiter(int maxAttempts, long windowSeconds) {
        this(maxAttempts, windowSeconds, Clock.systemUTC());
    }

    LoginRateLimiter(int maxAttempts, long windowSeconds, Clock clock) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowSeconds = Math.max(1L, windowSeconds);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void verificarYRegistrarIntento(String ipOrigen, String correo) {
        String ipKey = construirClave(PREFIX_IP, ipOrigen);
        String mailKey = construirClave(PREFIX_MAIL, correo);
        if (ipKey == null && mailKey == null) {
            return;
        }

        int limite = Math.max(1, maxAttempts);
        long ventana = Math.max(1L, windowSeconds);
        Instant ahora = Instant.now(clock);
        Instant corte = ahora.minusSeconds(ventana);

        limpiarSiCorresponde(corte);

        if (ipKey != null) {
            registrarOExceder(ipKey, ahora, corte, limite, ventana);
        }
        if (mailKey != null) {
            try {
                registrarOExceder(mailKey, ahora, corte, limite, ventana);
            } catch (RateLimitExceededException ex) {
                if (ipKey != null) {
                    revertirIntento(ipKey, ahora, corte);
                }
                throw ex;
            }
        }
    }

    private void registrarOExceder(String key, Instant ahora, Instant corte, int limite, long ventana) {
        AtomicLong retryAfterHolder = new AtomicLong(-1L);
        intentosPorClave.compute(key, (k, deque) -> {
            Deque<Instant> actual = deque != null ? deque : new ArrayDeque<>();
            purgarExpirados(actual, corte);
            if (actual.size() >= limite) {
                retryAfterHolder.set(calcularRetryAfterSegundos(actual.peekFirst(), ahora, ventana));
                return actual.isEmpty() ? null : actual;
            }
            actual.addLast(ahora);
            return actual;
        });

        long retryAfter = retryAfterHolder.get();
        if (retryAfter > 0L) {
            throw new RateLimitExceededException(
                    "Demasiados intentos de autenticacion. Intente nuevamente en " + retryAfter + " segundos.",
                    retryAfter);
        }
    }

    private void revertirIntento(String key, Instant instante, Instant corte) {
        intentosPorClave.computeIfPresent(key, (k, deque) -> {
            deque.removeLastOccurrence(instante);
            purgarExpirados(deque, corte);
            return deque.isEmpty() ? null : deque;
        });
    }

    private void limpiarSiCorresponde(Instant corte) {
        int ops = contadorOperaciones.incrementAndGet();
        boolean excedeCapacidad = intentosPorClave.size() > MAX_KEYS_TRACKED;
        if (!excedeCapacidad && ops % CLEANUP_EVERY_OPERATIONS != 0) {
            return;
        }

        int revisadas = 0;
        for (String key : intentosPorClave.keySet()) {
            if (revisadas >= MAX_CLEANUP_SCAN) {
                break;
            }
            revisadas++;
            intentosPorClave.computeIfPresent(key, (k, deque) -> {
                purgarExpirados(deque, corte);
                return deque.isEmpty() ? null : deque;
            });
        }

        if (intentosPorClave.size() > MAX_KEYS_TRACKED) {
            recortarExcedente();
        }
    }

    private void recortarExcedente() {
        int eliminadas = 0;
        for (String key : intentosPorClave.keySet()) {
            if (intentosPorClave.size() <= MAX_KEYS_TRACKED || eliminadas >= MAX_CLEANUP_SCAN) {
                break;
            }
            if (intentosPorClave.remove(key) != null) {
                eliminadas++;
            }
        }
    }

    private void purgarExpirados(Deque<Instant> deque, Instant corte) {
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(corte)) {
            deque.pollFirst();
        }
    }

    private long calcularRetryAfterSegundos(Instant masAntiguo, Instant ahora, long ventana) {
        if (masAntiguo == null) {
            return ventana;
        }
        Instant desbloqueo = masAntiguo.plusSeconds(ventana);
        long millisRestantes = Duration.between(ahora, desbloqueo).toMillis();
        if (millisRestantes <= 0L) {
            return 1L;
        }
        return (millisRestantes + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND;
    }

    private String construirClave(String prefijo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return prefijo + valor.trim().toLowerCase(Locale.ROOT);
    }
}
