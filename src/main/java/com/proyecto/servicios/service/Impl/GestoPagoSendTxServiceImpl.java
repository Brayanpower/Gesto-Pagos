package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoSendTx;
import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult.EstadoProveedor;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;
import com.proyecto.servicios.security.LogSanitizer;
import com.proyecto.servicios.service.EncryptionHelper;
import com.proyecto.servicios.service.GestoPagoSendTxService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.net.ssl.SSLException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class GestoPagoSendTxServiceImpl implements GestoPagoSendTxService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> CODIGOS_APROBADOS = Set.of("01", "00", "0");
    private static final Set<String> CODIGOS_EN_PROCESO = Set.of("02", "03", "70", "99");

    private final GestoPagoSendTx gestoPagoSendTx;
    private final EncryptionHelper encryptionHelper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String sendTx(SendTx request) {
        MultiValueMap<String, String> form = prepararFormularioFirmado(request);
        ResponseEntity<String> response = gestoPagoSendTx.sendTx(form);
        return response != null ? response.getBody() : null;
    }

    @Override
    public GestoPagoTxResult ejecutarTransaccion(SendTx request) {
        MultiValueMap<String, String> form = prepararFormularioFirmado(request);

        try {
            log.info("Enviando transaccion a GestoPago sendTx.do [upc={}, idServicio={}, idProducto={}]",
                    request.getUpc(), request.getIdServicio(), request.getIdProducto());

            ResponseEntity<String> response = gestoPagoSendTx.sendTx(form);

            if (response == null) {
                log.warn("Respuesta HTTP nula de GestoPago para upc={}; se marca para conciliacion", request.getUpc());
                return resultadoIndeterminado("SIN_RESPUESTA",
                        "No se recibio confirmacion completa del proveedor; operacion enviada a conciliacion.");
            }

            int status = response.getStatusCode().value();
            if (status == 400 || status == 401 || status == 403 || status == 404) {
                throw new ProveedorExternoException(
                        "El proveedor de servicios rechazo la solicitud antes de procesarla (HTTP " + status + ").");
            }
            if (status >= 500) {
                log.warn("GestoPago respondio HTTP {} en sendTx.do para upc={}; requiere conciliacion", status, request.getUpc());
                return resultadoIndeterminado("HTTP_" + status,
                        "El proveedor reporto un error temporal al responder; operacion en conciliacion.");
            }

            return interpretarRespuestaProveedor(response.getBody(), request.getUpc());

        } catch (ProveedorExternoException ex) {
            throw ex;
        } catch (FeignException ex) {
            return clasificarFalloTransporte(ex, request.getUpc());
        } catch (Exception ex) {
            if (esFalloPrevioAConexion(ex)) {
                log.error("Fallo de conexion previo al envio para upc={}: {}",
                        request.getUpc(), LogSanitizer.sanitize(ex.getMessage()));
                throw new ProveedorExternoException(
                        "No fue posible establecer conexion con el proveedor de servicios. No se aplico ningun cargo.");
            }
            log.error("Error indeterminado durante comunicacion con GestoPago para upc={} ({}): {}",
                    request.getUpc(), ex.getClass().getSimpleName(), LogSanitizer.sanitize(ex.getMessage()));
            return resultadoIndeterminado("TIMEOUT_O_RED",
                    "Tiempo de espera agotado o interrupcion de red con el proveedor; operacion en conciliacion.");
        }
    }

    @Override
    public GestoPagoTxResult verificarTransaccion(SendTx request) {
        MultiValueMap<String, String> form = prepararFormularioFirmado(request);
        try {
            log.info("Consultando estado de transaccion en GestoPago verifyTx [upc={}]", request.getUpc());
            ResponseEntity<String> response = gestoPagoSendTx.verifyTx(form);
            if (response == null || response.getStatusCode().value() >= 500) {
                return resultadoIndeterminado("VERIFY_NO_DISPONIBLE",
                        "El proveedor aun no confirma el estatus definitivo de la transaccion.");
            }
            if (response.getStatusCode().value() >= 400) {
                return resultadoIndeterminado("VERIFY_HTTP_" + response.getStatusCode().value(),
                        "No fue posible verificar la transaccion con el proveedor en este intento.");
            }
            return interpretarRespuestaProveedor(response.getBody(), request.getUpc());
        } catch (Exception ex) {
            log.warn("Consulta de conciliacion sin respuesta definitiva para upc={} ({})",
                    request.getUpc(), ex.getClass().getSimpleName());
            return resultadoIndeterminado("VERIFY_PENDIENTE",
                    "El proveedor no devolvio confirmacion definitiva durante la conciliacion.");
        }
    }

    private MultiValueMap<String, String> prepararFormularioFirmado(SendTx request) {
        if (request.getHoraLocal() == null || request.getHoraLocal().isBlank()) {
            request.setHoraLocal(LocalDateTime.now().format(HORA_LOCAL));
        }
        if (request.getUpc() == null || request.getUpc().isBlank()) {
            request.setUpc(generarUpc());
        }

        String signedPayload;
        try {
            signedPayload = encryptionHelper.encrypt(request);
        } catch (Exception ex) {
            throw new ProveedorExternoException(
                    "Error al proteger criptograficamente la solicitud antes del envio.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("random", String.valueOf(System.currentTimeMillis()));
        form.add("signed", signedPayload);
        return form;
    }

    private GestoPagoTxResult clasificarFalloTransporte(FeignException ex, String upc) {
        int status = ex.status();
        if (status == 400 || status == 401 || status == 403 || status == 404) {
            log.error("Rechazo HTTP {} de GestoPago previo al procesamiento para upc={}", status, upc);
            throw new ProveedorExternoException(
                    "El proveedor rechazo la comunicacion de integracion (HTTP " + status + "). No se realizo el cargo.");
        }

        if (esFalloPrevioAConexion(ex)) {
            log.error("Conexion rechazada antes de enviar payload a GestoPago para upc={}", upc);
            throw new ProveedorExternoException(
                    "El servicio externo de pagos no se encuentra alcanzable en este momento. Su saldo no fue afectado.");
        }

        log.error("Timeout o error indeterminado HTTP ({}) en GestoPago para upc={}; se envia a conciliacion",
                status, upc);
        return resultadoIndeterminado(
                status > 0 ? "HTTP_" + status : "TIMEOUT",
                "Tiempo de espera agotado al aguardar respuesta del proveedor; transaccion registrada para conciliacion."
        );
    }

    private boolean esFalloPrevioAConexion(Throwable throwable) {
        Throwable actual = throwable;
        int profundidad = 0;
        while (actual != null && profundidad < 10) {
            if (actual instanceof ConnectException
                    || actual instanceof UnknownHostException
                    || actual instanceof NoRouteToHostException
                    || actual instanceof SSLException) {
                return true;
            }
            actual = actual.getCause();
            profundidad++;
        }
        return false;
    }

    GestoPagoTxResult interpretarRespuestaProveedor(String rawBody, String upc) {
        if (rawBody == null || rawBody.isBlank()) {
            log.warn("Cuerpo de respuesta vacio recibido de GestoPago para upc={}", upc);
            return resultadoIndeterminado("RESPUESTA_VACIA",
                    "El proveedor devolvio una respuesta vacia; operacion marcada para conciliacion.");
        }

        String contenido = resolverContenidoDescifradoSiAplica(rawBody.trim());

        if (contenido.startsWith("<")) {
            return parsearXmlSeguramente(contenido, upc);
        }
        if (contenido.startsWith("{")) {
            return parsearJson(contenido, upc);
        }

        log.warn("Formato de respuesta desconocido recibido de GestoPago para upc={}", upc);
        return resultadoIndeterminado("FORMATO_DESCONOCIDO",
                "No fue posible confirmar el resultado devuelto por el proveedor; operacion en conciliacion.");
    }

    private String resolverContenidoDescifradoSiAplica(String body) {
        if (body.startsWith("<") || body.startsWith("{")) {
            return body;
        }
        try {
            String descifrado = encryptionHelper.decryptToString(body);
            if (descifrado != null && !descifrado.isBlank()) {
                return descifrado.trim();
            }
        } catch (Exception ignored) {
            // Si no estaba cifrado en Base64 AES, conservamos original para evaluacion segura
        }
        return body;
    }

    private GestoPagoTxResult parsearXmlSeguramente(String xml, String upc) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Proteccion XXE (OWASP Top 10)
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            String codigo = extraerTagXml(doc, "CODIGO");
            String texto = extraerTagXml(doc, "TEXTO");
            if (texto == null) {
                texto = extraerTagXml(doc, "MENSAJE");
            }
            String idTx = extraerTagXml(doc, "ID_TX");
            if (idTx == null) {
                idTx = extraerTagXml(doc, "TRANSACCION_ID");
            }
            String numAutorizacion = extraerTagXml(doc, "NUM_AUTORIZACION");
            if (numAutorizacion == null) {
                numAutorizacion = extraerTagXml(doc, "AUTORIZACION");
            }

            return construirResultadoPorCodigo(codigo, texto, idTx, numAutorizacion, upc);
        } catch (Exception ex) {
            log.warn("Error al interpretar XML de GestoPago para upc={} ({})", upc, ex.getClass().getSimpleName());
            return resultadoIndeterminado("XML_INVALIDO",
                    "Respuesta incompleta del proveedor; operacion retenida para conciliacion.");
        }
    }

    private GestoPagoTxResult parsearJson(String json, String upc) {
        try {
            JsonNode root = objectMapper.readTree(json);

            if (root.hasNonNull("signed") && !root.has("MENSAJE") && !root.has("codigo") && !root.has("CODIGO")) {
                String decrypted = encryptionHelper.decryptToString(root.get("signed").asText());
                if (decrypted != null && !decrypted.isBlank()) {
                    return interpretarRespuestaProveedor(decrypted.trim(), upc);
                }
            }

            JsonNode nodoMensaje = root.has("MENSAJE") ? root.get("MENSAJE")
                    : (root.has("mensaje") && root.get("mensaje").isObject() ? root.get("mensaje") : root);

            String codigo = extraerCampoJson(nodoMensaje, "CODIGO", "codigo", "code");
            if (codigo == null) {
                codigo = extraerCampoJson(root, "CODIGO", "codigo", "code");
            }
            String texto = extraerCampoJson(nodoMensaje, "TEXTO", "texto", "mensaje", "message");
            if (texto == null && root.has("mensaje") && root.get("mensaje").isTextual()) {
                texto = root.get("mensaje").asText();
            }
            String idTx = extraerCampoJson(root, "ID_TX", "idTx", "transaccionId", "TRANSACCION_ID");
            String numAut = extraerCampoJson(root, "NUM_AUTORIZACION", "numAutorizacion", "autorizacion");

            return construirResultadoPorCodigo(codigo, texto, idTx, numAut, upc);
        } catch (Exception ex) {
            log.warn("Error al interpretar JSON de GestoPago para upc={} ({})", upc, ex.getClass().getSimpleName());
            return resultadoIndeterminado("JSON_INVALIDO",
                    "Respuesta ilegible del proveedor; operacion enviada a conciliacion.");
        }
    }

    private GestoPagoTxResult construirResultadoPorCodigo(
            String codigo, String texto, String idTx, String numAutorizacion, String upc) {
        if (codigo == null || codigo.isBlank()) {
            log.warn("Respuesta de GestoPago sin codigo de operacion para upc={}", upc);
            return resultadoIndeterminado("SIN_CODIGO",
                    "El proveedor no indico codigo de estatus; operacion enviada a conciliacion.");
        }

        String codigoLimpio = codigo.trim();
        String mensajeSeguro = (texto != null && !texto.isBlank())
                ? LogSanitizer.sanitize(texto.trim())
                : "Respuesta procesada por proveedor";

        if (CODIGOS_APROBADOS.contains(codigoLimpio)) {
            return GestoPagoTxResult.builder()
                    .estado(EstadoProveedor.APROBADA)
                    .codigo(codigoLimpio)
                    .mensaje(mensajeSeguro)
                    .idTx(idTx)
                    .numAutorizacion(numAutorizacion)
                    .build();
        }

        if (CODIGOS_EN_PROCESO.contains(codigoLimpio)) {
            return GestoPagoTxResult.builder()
                    .estado(EstadoProveedor.PENDIENTE_PROVEEDOR)
                    .codigo(codigoLimpio)
                    .mensaje(mensajeSeguro)
                    .idTx(idTx)
                    .numAutorizacion(numAutorizacion)
                    .build();
        }

        return GestoPagoTxResult.builder()
                .estado(EstadoProveedor.RECHAZADA_PROVEEDOR)
                .codigo(codigoLimpio)
                .mensaje(mensajeSeguro)
                .idTx(idTx)
                .numAutorizacion(numAutorizacion)
                .build();
    }

    private static String extraerTagXml(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
            String val = nodes.item(0).getTextContent().trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private static String extraerCampoJson(JsonNode node, String... nombres) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String nombre : nombres) {
            if (node.hasNonNull(nombre) && node.get(nombre).isValueNode()) {
                String valor = node.get(nombre).asText().trim();
                if (!valor.isEmpty()) {
                    return valor;
                }
            }
        }
        return null;
    }

    private static GestoPagoTxResult resultadoIndeterminado(String codigo, String mensaje) {
        return GestoPagoTxResult.builder()
                .estado(EstadoProveedor.PENDIENTE_PROVEEDOR)
                .codigo(codigo)
                .mensaje(mensaje)
                .build();
    }

    private String generarUpc() {
        long numero = 1_000_000_000L + (long) (secureRandom.nextDouble() * 9_000_000_000L);
        return String.valueOf(numero);
    }
}
