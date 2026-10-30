package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.security.LogSanitizer;
import com.proyecto.servicios.service.EncryptionHelper;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final long MARGEN_EXPIRACION_SEGUNDOS = 60L;

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;
    private final EncryptionHelper encryptionHelper;
    private final ReentrantLock tokenLock = new ReentrantLock();

    @Value("${gestopago.auth.id-distribuidor:0}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo:}")
    private String codigoDispositivo;

    @Value("${gestopago.auth.password:}")
    private String password;

    @Value("${gestopago.auth.bearer-token:}")
    private String staticBearerToken;

    @Value("${gestopago.auth.refresh-rate-ms:3600000}")
    private long refreshRateMs;

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient,
                                     GestoPagoTokenRepository tokenRepository,
                                     GestoPagoTokenMapper tokenMapper,
                                     EncryptionHelper encryptionHelper) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.encryptionHelper = encryptionHelper;
    }

    @Override
    @Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}", initialDelay = 0)
    public void renovarToken() {
        if (tieneBearerEstatico()) {
            log.debug("Token Bearer provisto por configuracion segura externa; se omite renovacion programada");
            return;
        }
        if (idDistribuidor == null || idDistribuidor <= 0 || codigoDispositivo == null || codigoDispositivo.isBlank()
                || password == null || password.isBlank()) {
            log.warn("Credenciales de GestoPago no configuradas en el entorno; se omite renovacion de token");
            return;
        }

        tokenLock.lock();
        try {
            log.info("Renovando token GestoPago para distribuidor={}", idDistribuidor);
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password);

            if (response == null || response.getToken() == null || response.getToken().isBlank()) {
                log.error("La respuesta de autenticacion de GestoPago no contiene un token valido");
                return;
            }

            String tokenCifrado = cifrarToken(limpiarPrefijoBearer(response.getToken()));

            GestoPagoToken tokenEntity = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(existing -> {
                        tokenMapper.updateEntity(response, existing);
                        existing.setToken(tokenCifrado);
                        existing.setActivo(true);
                        return existing;
                    })
                    .orElseGet(() -> {
                        GestoPagoToken nuevo = tokenMapper.toEntity(response);
                        nuevo.setToken(tokenCifrado);
                        nuevo.setIdDistribuidor(idDistribuidor);
                        nuevo.setCodigoDispositivo(codigoDispositivo);
                        nuevo.setActivo(true);
                        return nuevo;
                    });

            tokenRepository.save(tokenEntity);
            log.info("Token GestoPago renovado y resguardado de forma segura");

        } catch (Exception e) {
            log.error("Error al renovar token GestoPago ({}): {}",
                    e.getClass().getSimpleName(), LogSanitizer.sanitize(e.getMessage()));
        } finally {
            tokenLock.unlock();
        }
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo) {
        return tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .filter(t -> Boolean.TRUE.equals(t.getActivo()));
    }

    @Override
    public String obtenerBearerTokenValido() {
        if (tieneBearerEstatico()) {
            return limpiarPrefijoBearer(staticBearerToken);
        }

        Optional<GestoPagoToken> actual = obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        if (actual.isPresent() && esTokenVigente(actual.get())) {
            return descifrarToken(actual.get().getToken());
        }

        tokenLock.lock();
        try {
            Optional<GestoPagoToken> revalidado = obtenerTokenActivo(idDistribuidor, codigoDispositivo);
            if (revalidado.isPresent() && esTokenVigente(revalidado.get())) {
                return descifrarToken(revalidado.get().getToken());
            }

            renovarToken();

            GestoPagoToken renovado = obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                    .filter(this::esTokenVigente)
                    .orElseThrow(() -> new ProveedorExternoException(
                            "No fue posible establecer autenticacion segura con el proveedor de servicios."));

            return descifrarToken(renovado.getToken());
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean tieneBearerEstatico() {
        return staticBearerToken != null && !staticBearerToken.isBlank();
    }

    private boolean esTokenVigente(GestoPagoToken tokenEntity) {
        if (tokenEntity.getToken() == null || tokenEntity.getToken().isBlank()) {
            return false;
        }
        LocalDateTime referencia = tokenEntity.getFechaActualizacion() != null
                ? tokenEntity.getFechaActualizacion()
                : tokenEntity.getFechaCreacion();
        if (referencia == null) {
            return true;
        }
        long duracionSegundos = (tokenEntity.getExpiresIn() != null && tokenEntity.getExpiresIn() > MARGEN_EXPIRACION_SEGUNDOS)
                ? (tokenEntity.getExpiresIn() - MARGEN_EXPIRACION_SEGUNDOS)
                : Math.max(60L, (refreshRateMs / 1000L));
        return referencia.plusSeconds(duracionSegundos).isAfter(LocalDateTime.now());
    }

    private String cifrarToken(String plainToken) {
        return ENCRYPTED_PREFIX + encryptionHelper.encrypt(plainToken);
    }

    private String descifrarToken(String storedToken) {
        if (storedToken == null) {
            throw new ProveedorExternoException("Token de proveedor invalido");
        }
        if (storedToken.startsWith(ENCRYPTED_PREFIX)) {
            return encryptionHelper.decryptToString(storedToken.substring(ENCRYPTED_PREFIX.length()));
        }
        return limpiarPrefijoBearer(storedToken);
    }

    private static String limpiarPrefijoBearer(String token) {
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
