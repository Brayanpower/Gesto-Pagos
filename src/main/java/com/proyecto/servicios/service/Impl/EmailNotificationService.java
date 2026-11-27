package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.notificacion.ComprobantePagoEmailData;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.security.LogSanitizer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class EmailNotificationService {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String HEADER_BRAND = "ManoLocal · Sistema Bancario y GestoPago";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled = true;

    @Value("${app.mail.from:}")
    private String mailFrom = "";

    @Value("${spring.mail.host:}")
    private String smtpHost = "";

    @Value("${spring.mail.username:}")
    private String smtpUsername = "";

    @Value("${spring.mail.password:}")
    private String smtpPassword = "";

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Envia de forma sincrona el codigo OTP temporal de 6 digitos por correo electronico.
     * Lanza ReglaNegocioException si el servicio SMTP no esta configurado o falla el envio.
     */
    public void enviarCodigoOtpCorreo(
            String destinatario,
            String nombre,
            String codigoOtp,
            long minutosVigencia,
            String proposito) {
        String propositoSeguro = esc(hasText(proposito) ? proposito : "Verificacion de acceso 2FA");
        String nombreSeguro = esc(resolverNombre(nombre));
        String otpSeguro = esc(codigoOtp);
        long vigencia = Math.max(1L, minutosVigencia);

        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Recibimos una solicitud para: <strong>%s</strong>.</p>
                <div style="margin:20px 0;padding:16px;background:#f4f6f9;border-radius:8px;text-align:center;">
                  <div style="font-size:12px;color:#555;margin-bottom:6px;">Tu código temporal de un solo uso</div>
                  <div style="font-size:28px;font-weight:700;letter-spacing:6px;color:#111827;">%s</div>
                  <div style="font-size:12px;color:#6b7280;margin-top:8px;">Vigencia: %d minuto(s). No lo compartas con nadie.</div>
                </div>
                <p style="font-size:13px;color:#4b5563;">Si no realizaste esta solicitud, ignora este correo y cambia tu contraseña de inmediato.</p>
                """.formatted(nombreSeguro, propositoSeguro, otpSeguro, vigencia);

        enviarHtml(
                destinatario,
                "Código de verificación 2FA - " + limpiarHeader(hasText(proposito) ? proposito : "Acceso"),
                envolverPlantilla("Código de verificación", cuerpo),
                true);
    }

    /**
     * Envia por correo los 8 codigos de respaldo emitidos tras completar el primer enrolamiento TOTP.
     */
    @Async("mailTaskExecutor")
    public void enviarCodigosRecuperacion2fa(String destinatario, String nombre, List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return;
        }
        StringBuilder listaHtml = new StringBuilder("<ul style=\"font-family:monospace;font-size:15px;line-height:1.8;\">");
        for (String codigo : codigos) {
            listaHtml.append("<li>").append(esc(codigo)).append("</li>");
        }
        listaHtml.append("</ul>");

        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Completaste la configuración inicial de autenticación multifactor (2FA TOTP). Guarda estos <strong>%d códigos de respaldo de un solo uso</strong> en un lugar seguro:</p>
                <div style="margin:16px 0;padding:12px 20px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;">
                  %s
                </div>
                <p style="font-size:13px;color:#4b5563;">Cada código solo puede utilizarse una vez para recuperar el acceso si pierdes tu app autenticadora.</p>
                """.formatted(esc(resolverNombre(nombre)), codigos.size(), listaHtml.toString());

        enviarHtml(
                destinatario,
                "Tus códigos de respaldo 2FA",
                envolverPlantilla("Respaldo de autenticación multifactor", cuerpo),
                false);
    }

    /**
     * Envia una alerta de seguridad ante inicio de sesion, cierre forzado de sesion previa o reset 2FA.
     */
    @Async("mailTaskExecutor")
    public void enviarAlertaSeguridad(
            String destinatario,
            String nombre,
            String tituloEvento,
            String detalle,
            ClientMetadata metadata,
            LocalDateTime fecha) {
        ClientMetadata meta = metadata != null ? metadata : ClientMetadata.desconocido();
        LocalDateTime momento = fecha != null ? fecha : LocalDateTime.now(ZoneId.systemDefault());
        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Detectamos la siguiente actividad de seguridad en tu cuenta:</p>
                <table style="width:100%%;border-collapse:collapse;margin:14px 0;font-size:14px;">
                  <tr><td style="padding:6px 0;color:#6b7280;">Evento:</td><td style="padding:6px 0;"><strong>%s</strong></td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Detalle:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Fecha y hora:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">IP de origen:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Dispositivo:</td><td style="padding:6px 0;">%s</td></tr>
                </table>
                <p style="font-size:13px;color:#4b5563;">Si no reconoces esta actividad, cierra tus sesiones activas y contacta a soporte.</p>
                """.formatted(
                esc(resolverNombre(nombre)),
                esc(hasText(tituloEvento) ? tituloEvento : "Actividad de seguridad"),
                esc(hasText(detalle) ? detalle : "Evento registrado en tu cuenta"),
                esc(FECHA_FMT.format(momento)),
                esc(meta.ipOrigen()),
                esc(meta.userAgent()));

        enviarHtml(
                destinatario,
                "Alerta de seguridad: " + limpiarHeader(hasText(tituloEvento) ? tituloEvento : "Actividad en tu cuenta"),
                envolverPlantilla("Alerta de seguridad", cuerpo),
                false);
    }

    /**
     * Envia el comprobante de una compra o pago de servicio GestoPago confirmado como EXITOSA.
     */
    @Async("mailTaskExecutor")
    public void enviarComprobantePagoServicio(
            String destinatario,
            String nombre,
            ComprobantePagoEmailData comprobante) {
        if (comprobante == null) {
            return;
        }
        LocalDateTime fechaOp = comprobante.fechaOperacion() != null
                ? comprobante.fechaOperacion()
                : LocalDateTime.now(ZoneId.systemDefault());
        String cuentaEnmascarada = LogSanitizer.maskAccount(comprobante.numeroCuenta());
        String referenciaOTel = hasText(comprobante.referencia())
                ? comprobante.referencia()
                : (hasText(comprobante.telefono()) ? comprobante.telefono() : "N/A");

        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu pago de servicio / compra se procesó correctamente. Aquí tienes el detalle de tu comprobante:</p>
                <table style="width:100%%;border-collapse:collapse;margin:14px 0;font-size:14px;">
                  <tr><td style="padding:6px 0;color:#6b7280;">Folio UPC:</td><td style="padding:6px 0;"><strong>%s</strong></td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Servicio:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Producto:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Monto:</td><td style="padding:6px 0;"><strong>$%s</strong></td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Cuenta:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Referencia / Teléfono:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Autorización proveedor:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Folio proveedor:</td><td style="padding:6px 0;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Saldo disponible:</td><td style="padding:6px 0;">$%s</td></tr>
                  <tr><td style="padding:6px 0;color:#6b7280;">Fecha y hora:</td><td style="padding:6px 0;">%s</td></tr>
                </table>
                """.formatted(
                esc(resolverNombre(nombre)),
                esc(valorO(comprobante.upc(), "N/A")),
                esc(valorO(comprobante.nombreServicio(), "Servicio GestoPago")),
                esc(valorO(comprobante.nombreProducto(), "Producto")),
                esc(formatearMonto(comprobante.monto())),
                esc(cuentaEnmascarada),
                esc(referenciaOTel),
                esc(valorO(comprobante.numAutorizacion(), "N/A")),
                esc(valorO(comprobante.folioProveedor(), "N/A")),
                esc(formatearMonto(comprobante.saldoDisponible())),
                esc(FECHA_FMT.format(fechaOp)));

        enviarHtml(
                destinatario,
                "Comprobante de pago exitoso - UPC " + limpiarHeader(valorO(comprobante.upc(), "")),
                envolverPlantilla("Comprobante de operación exitosa", cuerpo),
                false);
    }

    /**
     * Notifica al cliente que su solicitud de onboarding fue recibida y su cuenta quedo en revision.
     */
    @Async("mailTaskExecutor")
    public void enviarNotificacionOnboardingRecibido(
            String destinatario,
            String nombre,
            String numeroCuenta,
            BigDecimal saldoInicial) {
        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Recibimos tu solicitud de registro correctamente. Tu cuenta de ahorro terminación <strong>%s</strong> quedó registrada con estatus <strong>PENDIENTE_REVISION</strong> y saldo inicial de <strong>$%s</strong>.</p>
                <p style="font-size:13px;color:#4b5563;">Te notificaremos por este medio en cuanto un administrador concluya la revisión de tu cuenta.</p>
                """.formatted(
                esc(resolverNombre(nombre)),
                esc(LogSanitizer.maskAccount(numeroCuenta)),
                esc(formatearMonto(saldoInicial)));

        enviarHtml(
                destinatario,
                "Solicitud de onboarding recibida",
                envolverPlantilla("Onboarding en revisión", cuerpo),
                false);
    }

    /**
     * Notifica al cliente el dictamen administrativo sobre el estatus de su cuenta bancaria.
     */
    @Async("mailTaskExecutor")
    public void enviarNotificacionDictamenCuenta(
            String destinatario,
            String nombre,
            String numeroCuenta,
            EstatusCuenta nuevoEstatus,
            String observacion) {
        String estatusTexto = nuevoEstatus != null ? nuevoEstatus.name() : "ACTUALIZADA";
        String obsTexto = hasText(observacion) ? observacion : "Sin observaciones adicionales";

        String cuerpo = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Te informamos que tu cuenta de ahorro terminación <strong>%s</strong> cambió al estatus: <strong>%s</strong>.</p>
                <p><strong>Observación:</strong> %s</p>
                """.formatted(
                esc(resolverNombre(nombre)),
                esc(LogSanitizer.maskAccount(numeroCuenta)),
                esc(estatusTexto),
                esc(obsTexto));

        enviarHtml(
                destinatario,
                "Actualización de estatus de tu cuenta: " + limpiarHeader(estatusTexto),
                envolverPlantilla("Dictamen de cuenta de ahorro", cuerpo),
                false);
    }

    private boolean estaConfigurado() {
        return mailEnabled
                && mailSenderProvider.getIfAvailable() != null
                && hasText(smtpHost)
                && hasText(smtpUsername)
                && hasText(smtpPassword);
    }

    private void enviarHtml(String destinatario, String asunto, String htmlBody, boolean lanzarSiFalla) {
        if (!hasText(destinatario)) {
            if (lanzarSiFalla) {
                throw new ReglaNegocioException("El usuario no cuenta con un correo electronico valido para recibir el codigo OTP.");
            }
            return;
        }
        if (!estaConfigurado()) {
            if (lanzarSiFalla) {
                throw new ReglaNegocioException("El servicio de correo SMTP no esta configurado en el servidor.");
            }
            log.debug("Envio de correo omitido (SMTP no configurado o deshabilitado)");
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            if (lanzarSiFalla) {
                throw new ReglaNegocioException("El cliente SMTP no esta disponible en el servidor.");
            }
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            String fromEfectivo = hasText(mailFrom) ? mailFrom.trim() : smtpUsername.trim();
            helper.setFrom(new InternetAddress(fromEfectivo));
            helper.setTo(destinatario.trim());
            helper.setSubject(limpiarHeader(asunto));
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MailException | MessagingException ex) {
            log.warn("Error al enviar correo electronico ({})", ex.getClass().getSimpleName());
            if (lanzarSiFalla) {
                throw new ReglaNegocioException("No fue posible enviar el codigo de verificacion por correo electronico.");
            }
        }
    }

    private String envolverPlantilla(String titulo, String contenidoHtml) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;color:#1f2937;">
                  <div style="font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#2563eb;font-weight:700;">%s</div>
                  <h2 style="margin:8px 0 16px 0;font-size:20px;color:#111827;">%s</h2>
                  %s
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0 12px 0;" />
                  <div style="font-size:11px;color:#9ca3af;">Mensaje automático generado por el servidor. Por favor no respondas a este correo.</div>
                </div>
                """.formatted(esc(HEADER_BRAND), esc(titulo), contenidoHtml);
    }

    private static String resolverNombre(String nombre) {
        return hasText(nombre) ? nombre.trim() : "Usuario";
    }

    private static String valorO(String valor, String fallback) {
        return hasText(valor) ? valor.trim() : fallback;
    }

    private static String formatearMonto(BigDecimal monto) {
        if (monto == null) {
            return "0.00";
        }
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String esc(String valor) {
        return valor == null ? "" : HtmlUtils.htmlEscape(valor);
    }

    private static String limpiarHeader(String valor) {
        return valor == null ? "" : valor.replace("\r", " ").replace("\n", " ").trim();
    }

    private static boolean hasText(String valor) {
        return valor != null && !valor.isBlank();
    }
}
