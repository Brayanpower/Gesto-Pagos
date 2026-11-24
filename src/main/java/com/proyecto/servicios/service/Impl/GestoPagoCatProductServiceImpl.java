package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoCatProduct;
import com.proyecto.servicios.exception.GestoPagoCatProductException;
import com.proyecto.servicios.model.gestopago.catproduct.GestoPagoCatProductResponse;
import com.proyecto.servicios.security.LogSanitizer;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.GestoPagoCatProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class GestoPagoCatProductServiceImpl implements GestoPagoCatProductService {

    private static final int MAX_REINTENTOS = 3;
    private static final long ESPERA_ENTRE_REINTENTOS_MS = 500L;
    private static final Set<String> CODIGOS_EXITO = Set.of("01", "00", "0");
    private static final JAXBContext JAXB_CONTEXT = crearJaxbContext();
    private static final XMLInputFactory SECURE_XML_INPUT_FACTORY = crearXmlInputFactory();

    private final GestoPagoCatProduct gestoPagoCatProduct;
    private final CatalogoService catalogoService;
    private final GestoPagoTokenService gestoPagoTokenService;

    @Value("${gestopago.catalogo.startup-delay-ms:3000}")
    private long inicioEsperaMs;

    private final AtomicReference<GestoPagoCatProductResponse> catalogoCache = new AtomicReference<>();

    public GestoPagoCatProductServiceImpl(GestoPagoCatProduct gestoPagoCatProduct,
                                          CatalogoService catalogoService,
                                          GestoPagoTokenService gestoPagoTokenService) {
        this.gestoPagoCatProduct = gestoPagoCatProduct;
        this.catalogoService = catalogoService;
        this.gestoPagoTokenService = gestoPagoTokenService;
    }

    @Override
    public GestoPagoCatProductResponse obtenerCatalogoProductos() {
        int intento = 0;
        while (intento < MAX_REINTENTOS) {
            intento++;
            log.info("Obteniendo catalogo de productos GestoPago, intento {}/{}", intento, MAX_REINTENTOS);
            try {
                GestoPagoCatProductResponse response = llamarYValidar();
                log.info("Catalogo de productos obtenido correctamente ({} productos)",
                        response.getProductos().getProductos().size());
                return response;
            } catch (GestoPagoCatProductException ex) {
                log.error("Intento {}/{} fallido: {}", intento, MAX_REINTENTOS, ex.getMessage());
                if (intento == MAX_REINTENTOS) {
                    throw ex;
                }
                esperar();
            } catch (Exception ex) {
                log.error("Intento {}/{} fallido por error inesperado: {}", intento, MAX_REINTENTOS, ex.getMessage(), ex);
                if (intento == MAX_REINTENTOS) {
                    throw new GestoPagoCatProductException(
                            "No fue posible obtener el catalogo de GestoPago tras " + MAX_REINTENTOS + " intentos", ex);
                }
                esperar();
            }
        }
        throw new GestoPagoCatProductException(
                "No fue posible obtener el catalogo de GestoPago tras " + MAX_REINTENTOS + " intentos");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarCatalogoAlInicio() {
        Executor delayed = CompletableFuture.delayedExecutor(
                Math.max(0L, inicioEsperaMs), TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(this::cargarCatalogoProgramado, delayed);
    }

    @Scheduled(cron = "${gestopago.catalogo.cron:0 0 6 * * *}")
    public void cargarCatalogoProgramado() {
        log.info("Ejecutando carga programada del catalogo GestoPago (06:00 hrs)");
        try {
            GestoPagoCatProductResponse response = obtenerCatalogoProductos();
            this.catalogoCache.set(response);
            catalogoService.reemplazarCatalogo(response);
            log.info("Catalogo GestoPago cargado y persistido en MongoDB ({} productos)",
                    response.getProductos().getProductos().size());
        } catch (GestoPagoCatProductException ex) {
            log.error("No fue posible poblar el catalogo en la ejecucion programada: {}", ex.getMessage());
        }
    }

    @Override
    public GestoPagoCatProductResponse obtenerCatalogoCache() {
        GestoPagoCatProductResponse cache = this.catalogoCache.get();
        if (cache == null) {
            throw new GestoPagoCatProductException(
                    "El catalogo de GestoPago aun no ha sido cargado por el proceso programado de las 06:00 hrs");
        }
        return cache;
    }

    private GestoPagoCatProductResponse llamarYValidar() {
        ResponseEntity<String> respuestaHttp;
        try {
            respuestaHttp = gestoPagoCatProduct.getProductList();
        } catch (FeignException.Unauthorized ex) {
            gestoPagoTokenService.invalidarTokenActual();
            throw new GestoPagoCatProductException(
                    "Token de GestoPago rechazado (401) al consultar catalogo: "
                            + LogSanitizer.sanitize(ex.getMessage()),
                    ex);
        } catch (Exception ex) {
            throw new GestoPagoCatProductException(
                    "Error de conexion con el servicio GestoPago: " + LogSanitizer.sanitize(ex.getMessage()),
                    ex);
        }

        if (respuestaHttp == null || !HttpStatusCode.valueOf(200).equals(respuestaHttp.getStatusCode())) {
            int codigoHttp = respuestaHttp == null ? -1 : respuestaHttp.getStatusCode().value();
            if (codigoHttp == 401) {
                gestoPagoTokenService.invalidarTokenActual();
            }
            throw new GestoPagoCatProductException(
                    "El servicio GestoPago respondio con codigo HTTP " + codigoHttp
                            + "; no se modifica el catalogo, se conserva el anterior");
        }

        String xml = respuestaHttp.getBody();
        if (xml == null || xml.isBlank()) {
            throw new GestoPagoCatProductException("El servicio GestoPago respondio con un cuerpo vacio");
        }

        try {
            GestoPagoCatProductResponse response = deserializar(xml);
            validar(response);
            return response;
        } catch (JAXBException | XMLStreamException ex) {
            log.warn("XML recibido de GestoPago que no pudo mapearse (truncado): {}", truncar(xml));
            throw new GestoPagoCatProductException("No fue posible interpretar el XML devuelto por GestoPago", ex);
        } catch (GestoPagoCatProductException ex) {
            log.warn("XML recibido de GestoPago que no paso la validacion (truncado): {}", truncar(xml));
            throw ex;
        }
    }

    private static String truncar(String xml) {
        int max = 2000;
        return xml.length() <= max ? xml : xml.substring(0, max);
    }

    private void validar(GestoPagoCatProductResponse response) {
        if (response == null) {
            throw new GestoPagoCatProductException("El catalogo de GestoPago llego vacio o nulo");
        }
        if (response.getMensaje() == null || response.getMensaje().getCodigo() == null) {
            throw new GestoPagoCatProductException("La respuesta de GestoPago no contiene el mensaje de operacion");
        }
        String codigo = response.getMensaje().getCodigo().trim();
        if (!CODIGOS_EXITO.contains(codigo)) {
            throw new GestoPagoCatProductException(
                    "GestoPago reporto codigo de error al consultar el catalogo: " + LogSanitizer.sanitize(codigo));
        }
        if (response.getProductos() == null || response.getProductos().getProductos() == null
                || response.getProductos().getProductos().isEmpty()) {
            throw new GestoPagoCatProductException("El catalogo de GestoPago no contiene productos");
        }
    }

    private static GestoPagoCatProductResponse deserializar(String xml) throws JAXBException, XMLStreamException {
        XMLStreamReader reader = SECURE_XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
        try {
            Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
            return (GestoPagoCatProductResponse) unmarshaller.unmarshal(reader);
        } finally {
            reader.close();
        }
    }

    private static XMLInputFactory crearXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private void esperar() {
        try {
            Thread.sleep(ESPERA_ENTRE_REINTENTOS_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GestoPagoCatProductException("El reintento fue interrumpido", ex);
        }
    }

    private static JAXBContext crearJaxbContext() {
        try {
            return JAXBContext.newInstance(GestoPagoCatProductResponse.class);
        } catch (JAXBException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}