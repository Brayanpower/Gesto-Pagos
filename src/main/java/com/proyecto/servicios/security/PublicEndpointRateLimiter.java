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

/**
 * Limitador de tasa en memoria por IP para endpoints públicos sensibles (onboarding y proxy de imágenes).
 */
@Component
public class PublicEndpointRateLimiter {

    private static final int DEFAULT_ONBOARDING_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_ONBOARDING_WINDOW_SECONDS = 300L;
    private static final int DEFAULT_IMAGES_MAX_ATTEMPTS = 60;
    private static final long DEFAULT_IMAGES_WINDOW_SECONDS = 60L;
    private static final int CLEANUP_EVERY_OPERATIONS = 64;
    private static final int MAX_KEYS_TRACKED = 10_000;
    private static final int MAX_CLEANUP_SCAN = 256;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final String PREFIX_ONBOARDING = "onboarding:";
    private static final String PREFIX_IMAGES = "images:";

    private final ConcurrentHashMap<String, Deque<Instant>> solicitudesPorClave = new ConcurrentHashMap<>();
    private final AtomicInteger contadorOperaciones = new AtomicInteger(0);
    private final Clock clock;

    @Value("${app.rate-limit.onboarding.max-attempts:5}")
    private int onboardingMaxAttempts = DEFAULT_ONBOARDING_MAX_ATTEMPTS;

    @Value("${app.rate-limit.onboarding.window-seconds:300}")
    private long onboardingWindowSeconds = DEFAULT_ONBOARDING_WINDOW_SECONDS;

    @Value("${app.rate-limit.images.max-attempts:60}")
    private int imagesMaxAttempts = DEFAULT_IMAGES_MAX_ATTEMPTS;

    @Value("${app.rate-limit.images.window-seconds:60}")
    private long imagesWindowSeconds = DEFAULT_IMAGES_WINDOW_SECONDS;

    @Autowired
    public PublicEndpointRateLimiter() {
        this(
                DEFAULT_ONBOARDING_MAX_ATTEMPTS,
                DEFAULT_ONBOARDING_WINDOW_SECONDS,
                DEFAULT_IMAGES_MAX_ATTEMPTS,
                DEFAULT_IMAGES_WINDOW_SECONDS,
                Clock.systemUTC());
    }

    PublicEndpointRateLimiter(
            int onboardingMaxAttempts,
            long onboardingWindowSeconds,
            int imagesMaxAttempts,
            long imagesWindowSeconds,
            Clock clock) {
        this.onboardingMaxAttempts = Math.max(1, onboardingMaxAttempts);
        this.onboardingWindowSeconds = Math.max(1L, onboardingWindowSeconds);
        this.imagesMaxAttempts = Math.max(1, imagesMaxAttempts);
        this.imagesWindowSeconds = Math.max(1L, imagesWindowSeconds);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void verificarLimiteOnboarding(String ipOrigen) {
        verificarLimite(
                PREFIX_ONBOARDING,
                ipOrigen,
                onboardingMaxAttempts,
                onboardingWindowSeconds,
                "Demasiadas solicitudes de registro. Intente nuevamente en ");
    }

    public void verificarLimiteImagenes(String ipOrigen) {
        verificarLimite(
                PREFIX_IMAGES,
                ipOrigen,
                imagesMaxAttempts,
                imagesWindowSeconds,
                "Demasiadas solicitudes de imagenes. Intente nuevamente en ");
    }

    private void verificarLimite(
            String prefijo,
            String ipOrigen,
            int maxAttempts,
            long windowSeconds,
            String mensajePrefijo) {
        if (ipOrigen == null || ipOrigen.isBlank()) {
            return;
        }
        String key = prefijo + ipOrigen.trim().toLowerCase(Locale.ROOT);
        int limite = Math.max(1, maxAttempts);
        long ventana = Math.max(1L, windowSeconds);
        Instant ahora = Instant.now(clock);
        Instant corte = ahora.minusSeconds(ventana);

        limpiarSiCorresponde(corte);

        AtomicLong retryAfterHolder = new AtomicLong(-1L);
        solicitudesPorClave.compute(key, (k, deque) -> {
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
            throw new RateLimitExceededException(mensajePrefijo + retryAfter + " segundos.", retryAfter);
        }
    }

    private void limpiarSiCorresponde(Instant corte) {
        int ops = contadorOperaciones.incrementAndGet();
        boolean excedeCapacidad = solicitudesPorClave.size() > MAX_KEYS_TRACKED;
        if (!excedeCapacidad && ops % CLEANUP_EVERY_OPERATIONS != 0) {
            return;
        }
        int revisadas = 0;
        for (String key : solicitudesPorClave.keySet()) {
            if (revisadas >= MAX_CLEANUP_SCAN) {
                break;
            }
            revisadas++;
            solicitudesPorClave.computeIfPresent(key, (k, deque) -> {
                purgarExpirados(deque, corte);
                return deque.isEmpty() ? null : deque;
            });
        }
        if (solicitudesPorClave.size() > MAX_KEYS_TRACKED) {
            recortarExcedente();
        }
    }

    private void recortarExcedente() {
        int eliminadas = 0;
        for (String key : solicitudesPorClave.keySet()) {
            if (solicitudesPorClave.size() <= MAX_KEYS_TRACKED || eliminadas >= MAX_CLEANUP_SCAN) {
                break;
            }
            if (solicitudesPorClave.remove(key) != null) {
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
}
