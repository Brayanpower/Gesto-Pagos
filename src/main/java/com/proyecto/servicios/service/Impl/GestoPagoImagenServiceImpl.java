package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.security.LogSanitizer;
import com.proyecto.servicios.service.GestoPagoImagenService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GestoPagoImagenServiceImpl implements GestoPagoImagenService {

    private static final String PATH_LOGO_SERVICIO = "/sistema/images/gestopago/servicios/{idServicio}.png";
    private static final String PATH_IMAGEN_REFERENCIA = "/sistema/images/gestopago/referencias/{idServicio}.jpg";
    private static final String CACHE_PREFIX_LOGO = "logo:";
    private static final String CACHE_PREFIX_REFERENCIA = "ref:";
    private static final String HEADER_X_API_KEY = "X-API-Key";
    private static final String MEDIA_TYPE_IMAGE = "image";
    private static final String MSG_ID_SERVICIO_INVALIDO = "El idServicio debe ser un numero entero positivo.";
    private static final Pattern ID_SERVICIO_PATTERN = Pattern.compile("^[1-9]\\d{0,9}$");
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final int EVICTION_BATCH_SIZE = 50;
    private static final Duration CACHE_HIT_TTL = Duration.ofHours(24);
    private static final Duration CACHE_MISS_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final GestoPagoTokenService gestoPagoTokenService;
    private final String apiKey;
    private final ConcurrentHashMap<String, CacheEntry> cacheImagenes = new ConcurrentHashMap<>();

    @Autowired
    public GestoPagoImagenServiceImpl(
            RestClient.Builder restClientBuilder,
            GestoPagoTokenService gestoPagoTokenService,
            @Value("${gestopago.auth.url:https://gestopago.portalventas.net}") String baseUrl,
            @Value("${gestopago.auth.api-key:}") String apiKey,
            @Value("${gestopago.images.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${gestopago.images.read-timeout-ms:10000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new NoRedirectSimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(500, connectTimeoutMs)));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(500, readTimeoutMs)));

        this.restClient = restClientBuilder
                .clone()
                .baseUrl(normalizarBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.gestoPagoTokenService = gestoPagoTokenService;
        this.apiKey = normalizarApiKey(apiKey);
    }

    GestoPagoImagenServiceImpl(
            RestClient restClient,
            GestoPagoTokenService gestoPagoTokenService,
            String apiKey) {
        this.restClient = restClient;
        this.gestoPagoTokenService = gestoPagoTokenService;
        this.apiKey = normalizarApiKey(apiKey);
    }

    @Override
    public Optional<ImagenRecurso> obtenerLogoServicio(String idServicio) {
        return obtenerImagen(idServicio, CACHE_PREFIX_LOGO, PATH_LOGO_SERVICIO, MediaType.IMAGE_PNG);
    }

    @Override
    public Optional<ImagenRecurso> obtenerImagenReferencia(String idServicio) {
        return obtenerImagen(idServicio, CACHE_PREFIX_REFERENCIA, PATH_IMAGEN_REFERENCIA, MediaType.IMAGE_JPEG);
    }

    private Optional<ImagenRecurso> obtenerImagen(
            String idServicioRaw,
            String cachePrefix,
            String pathTemplate,
            MediaType mediaTypePorDefecto) {
        String idServicio = validarIdServicio(idServicioRaw);
        String cacheKey = cachePrefix + idServicio;
        Instant ahora = Instant.now();

        CacheEntry enCache = cacheImagenes.get(cacheKey);
        if (enCache != null && enCache.expiraEn().isAfter(ahora)) {
            return Optional.ofNullable(enCache.recurso());
        }

        ResultadoDescarga resultado = descargarConReintentoAuth(
                pathTemplate, idServicio, mediaTypePorDefecto);

        if (resultado.estado() == EstadoDescarga.FOUND && resultado.recurso() != null) {
            guardarEnCache(cacheKey, resultado.recurso(), ahora.plus(CACHE_HIT_TTL));
            return Optional.of(resultado.recurso());
        }
        if (resultado.estado() == EstadoDescarga.NOT_FOUND) {
            guardarEnCache(cacheKey, null, ahora.plus(CACHE_MISS_TTL));
        }
        return Optional.empty();
    }

    private ResultadoDescarga descargarConReintentoAuth(
            String pathTemplate,
            String idServicio,
            MediaType mediaTypePorDefecto) {
        try {
            String token = gestoPagoTokenService.obtenerBearerTokenValido();
            return ejecutarDescarga(pathTemplate, idServicio, token, mediaTypePorDefecto);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Token GestoPago rechazado (401) al consultar imagen de servicio={}; renovando token", idServicio);
            return reintentarConTokenRenovado(pathTemplate, idServicio, mediaTypePorDefecto);
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Imagen GestoPago no encontrada para servicio={}", idServicio);
            return ResultadoDescarga.notFound();
        } catch (ProveedorExternoException | RestClientException ex) {
            log.warn("No se pudo obtener imagen GestoPago para servicio={} [{}]: {}",
                    idServicio, ex.getClass().getSimpleName(), LogSanitizer.sanitize(ex.getMessage()));
            return ResultadoDescarga.transientError();
        }
    }

    private ResultadoDescarga reintentarConTokenRenovado(
            String pathTemplate,
            String idServicio,
            MediaType mediaTypePorDefecto) {
        try {
            gestoPagoTokenService.renovarToken();
            String tokenRenovado = gestoPagoTokenService.obtenerBearerTokenValido();
            return ejecutarDescarga(pathTemplate, idServicio, tokenRenovado, mediaTypePorDefecto);
        } catch (HttpClientErrorException.NotFound ex) {
            return ResultadoDescarga.notFound();
        } catch (ProveedorExternoException | RestClientException ex) {
            log.warn("Fallo al reintentar descarga de imagen GestoPago para servicio={} [{}]",
                    idServicio, ex.getClass().getSimpleName());
            return ResultadoDescarga.transientError();
        }
    }

    private ResultadoDescarga ejecutarDescarga(
            String pathTemplate,
            String idServicio,
            String bearerToken,
            MediaType mediaTypePorDefecto) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return ResultadoDescarga.transientError();
        }

        ResponseEntity<byte[]> response = restClient.get()
                .uri(pathTemplate, idServicio)
                .headers(headers -> aplicarHeadersAutenticacion(headers, bearerToken))
                .retrieve()
                .toEntity(byte[].class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            return response.getStatusCode().is5xxServerError()
                    ? ResultadoDescarga.transientError()
                    : ResultadoDescarga.notFound();
        }

        byte[] body = response.getBody();
        if (body == null || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
            return ResultadoDescarga.notFound();
        }

        MediaType contentType = resolverContentType(response.getHeaders().getContentType(), mediaTypePorDefecto);
        if (contentType == null) {
            log.warn("Contenido no valido recibido desde GestoPago para servicio={} (Content-Type={})",
                    idServicio, response.getHeaders().getContentType());
            return ResultadoDescarga.notFound();
        }

        return ResultadoDescarga.found(new ImagenRecurso(body, contentType));
    }

    private void aplicarHeadersAutenticacion(HttpHeaders headers, String bearerToken) {
        headers.setBearerAuth(bearerToken.trim());
        headers.setAccept(List.of(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.ALL));
        if (apiKey != null) {
            headers.set(HEADER_X_API_KEY, apiKey);
        }
    }

    private MediaType resolverContentType(MediaType upstreamType, MediaType defaultType) {
        if (upstreamType == null || MediaType.APPLICATION_OCTET_STREAM.includes(upstreamType)) {
            return defaultType;
        }
        if (MEDIA_TYPE_IMAGE.equalsIgnoreCase(upstreamType.getType())) {
            return new MediaType(upstreamType.getType(), upstreamType.getSubtype());
        }
        return null;
    }

    private String validarIdServicio(String idServicioRaw) {
        if (idServicioRaw == null) {
            throw new ReglaNegocioException(MSG_ID_SERVICIO_INVALIDO);
        }
        String limpio = idServicioRaw.trim();
        if (!ID_SERVICIO_PATTERN.matcher(limpio).matches()) {
            throw new ReglaNegocioException(MSG_ID_SERVICIO_INVALIDO);
        }
        return limpio;
    }

    private void guardarEnCache(String cacheKey, ImagenRecurso recurso, Instant expiraEn) {
        if (cacheImagenes.size() >= MAX_CACHE_ENTRIES) {
            limpiarCacheExpiradaOExcedente();
        }
        cacheImagenes.put(cacheKey, new CacheEntry(recurso, expiraEn));
    }

    private void limpiarCacheExpiradaOExcedente() {
        Instant ahora = Instant.now();
        cacheImagenes.entrySet().removeIf(entry -> !entry.getValue().expiraEn().isAfter(ahora));
        if (cacheImagenes.size() < MAX_CACHE_ENTRIES) {
            return;
        }
        int eliminadas = 0;
        for (String key : cacheImagenes.keySet()) {
            if (cacheImagenes.size() < MAX_CACHE_ENTRIES || eliminadas >= EVICTION_BATCH_SIZE) {
                break;
            }
            if (cacheImagenes.remove(key) != null) {
                eliminadas++;
            }
        }
    }

    private static String normalizarBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://gestopago.portalventas.net";
        }
        String limpia = baseUrl.trim();
        String lower = limpia.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new IllegalArgumentException("La URL base de GestoPago debe iniciar con https:// o http://");
        }
        return limpia;
    }

    private static String normalizarApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return apiKey.trim();
    }

    private enum EstadoDescarga {
        FOUND,
        NOT_FOUND,
        TRANSIENT_ERROR
    }

    private record ResultadoDescarga(EstadoDescarga estado, ImagenRecurso recurso) {
        private static ResultadoDescarga found(ImagenRecurso recurso) {
            return new ResultadoDescarga(EstadoDescarga.FOUND, recurso);
        }

        private static ResultadoDescarga notFound() {
            return new ResultadoDescarga(EstadoDescarga.NOT_FOUND, null);
        }

        private static ResultadoDescarga transientError() {
            return new ResultadoDescarga(EstadoDescarga.TRANSIENT_ERROR, null);
        }
    }

    private record CacheEntry(ImagenRecurso recurso, Instant expiraEn) {
    }

    private static class NoRedirectSimpleClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
