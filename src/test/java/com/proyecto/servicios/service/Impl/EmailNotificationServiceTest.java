package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.notificacion.ComprobantePagoEmailData;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de EmailNotificationService")
class EmailNotificationServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(mailSenderProvider);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "smtpHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(service, "smtpUsername", "remitente@gmail.com");
        ReflectionTestUtils.setField(service, "smtpPassword", "app-password");
        ReflectionTestUtils.setField(service, "mailFrom", "ManoLocal <remitente@gmail.com>");

        lenient().when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        lenient().when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @DisplayName("enviarCodigoOtpCorreo construye MIME con destinatario, remitente, HTML escapado y envia")
    void enviarCodigoOtpCorreo_enviaMensajeCorrecto() throws Exception {
        service.enviarCodigoOtpCorreo("cliente@banco.com", "<script>Juan</script>", "482910", 5L, "Inicio de sesion");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());

        MimeMessage msg = captor.getValue();
        assertEquals("cliente@banco.com", msg.getAllRecipients()[0].toString());
        assertTrue(msg.getFrom()[0].toString().contains("remitente@gmail.com"));
        assertTrue(msg.getSubject().contains("Inicio de sesion"));

        String body = extraerTexto(msg);
        assertTrue(body.contains("482910"));
        assertTrue(body.contains("&lt;script&gt;Juan&lt;/script&gt;"));
        assertFalse(body.contains("<script>Juan</script>"));
    }

    @Test
    @DisplayName("enviarCodigosRecuperacion2fa incluye todos los codigos de respaldo")
    void enviarCodigosRecuperacion2fa_enviaLista() throws Exception {
        service.enviarCodigosRecuperacion2fa("cliente@banco.com", "Juan", List.of("ABCD-2345", "WXYZ-6789"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());

        String body = extraerTexto(captor.getValue());
        assertTrue(body.contains("ABCD-2345"));
        assertTrue(body.contains("WXYZ-6789"));
    }

    @Test
    @DisplayName("enviarAlertaSeguridad incluye evento, IP y dispositivo")
    void enviarAlertaSeguridad_incluyeMetadatos() throws Exception {
        service.enviarAlertaSeguridad(
                "cliente@banco.com",
                "Juan",
                "Nuevo inicio de sesion",
                "Inicio correcto",
                ClientMetadata.of("198.51.100.8", "FlutterApp/1.0"),
                LocalDateTime.of(2026, 11, 26, 18, 0));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());

        String body = extraerTexto(captor.getValue());
        assertTrue(body.contains("198.51.100.8"));
        assertTrue(body.contains("FlutterApp/1.0"));
    }

    @Test
    @DisplayName("enviarComprobantePagoServicio enmascara cuenta y muestra UPC, monto y autorizacion")
    void enviarComprobantePagoServicio_enmascaraCuenta() throws Exception {
        ComprobantePagoEmailData data = new ComprobantePagoEmailData(
                "1002003004",
                "1234567890",
                "Telcel",
                "Recarga 100",
                new BigDecimal("100.00"),
                new BigDecimal("900.00"),
                "5512345678",
                null,
                "TX-900",
                "AUT-777",
                LocalDateTime.of(2026, 11, 26, 18, 5));

        service.enviarComprobantePagoServicio("cliente@banco.com", "Juan", data);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());

        String body = extraerTexto(captor.getValue());
        assertTrue(body.contains("1002003004"));
        assertTrue(body.contains("****7890"));
        assertFalse(body.contains("1234567890"));
        assertTrue(body.contains("AUT-777"));
        assertTrue(body.contains("$100.00"));
    }

    @Test
    @DisplayName("enviarNotificacionOnboardingRecibido y DictamenCuenta envian correos con cuenta enmascarada")
    void enviarNotificacionesOnboardingYDictamen() throws Exception {
        service.enviarNotificacionOnboardingRecibido("cliente@banco.com", "Juan", "1234567890", new BigDecimal("500.00"));
        service.enviarNotificacionDictamenCuenta("cliente@banco.com", "Juan", "1234567890", EstatusCuenta.ACTIVA, "Aprobada");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, org.mockito.Mockito.times(2)).send(captor.capture());

        String body1 = extraerTexto(captor.getAllValues().get(0));
        String body2 = extraerTexto(captor.getAllValues().get(1));
        assertTrue(body1.contains("PENDIENTE_REVISION"));
        assertTrue(body1.contains("****7890"));
        assertTrue(body2.contains("ACTIVA"));
        assertTrue(body2.contains("Aprobada"));
    }

    @Test
    @DisplayName("Cuando SMTP no esta configurado, OTP sincrono lanza ReglaNegocioException y notificaciones no fallan")
    void smtpNoConfigurado_comportamientoEsperado() {
        ReflectionTestUtils.setField(service, "smtpPassword", "");

        assertThrows(ReglaNegocioException.class,
                () -> service.enviarCodigoOtpCorreo("cliente@banco.com", "Juan", "123456", 5L, "Login"));

        assertDoesNotThrow(() -> service.enviarAlertaSeguridad(
                "cliente@banco.com", "Juan", "Evento", "Detalle", ClientMetadata.desconocido(), LocalDateTime.now()));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Ante fallo MailSendException, OTP sincrono lanza ReglaNegocioException y notificacion asincrona lo absorbe")
    void falloEnvioSmtp_otpLanzaYNotificacionAbsorbe() {
        doThrow(new MailSendException("SMTP caido")).when(javaMailSender).send(any(MimeMessage.class));

        assertThrows(ReglaNegocioException.class,
                () -> service.enviarCodigoOtpCorreo("cliente@banco.com", "Juan", "123456", 5L, "Login"));

        assertDoesNotThrow(() -> service.enviarNotificacionDictamenCuenta(
                "cliente@banco.com", "Juan", "1234567890", EstatusCuenta.ACTIVA, "Ok"));
    }

    private String extraerTexto(MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String str) {
            return str;
        }
        if (content instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Object part = multipart.getBodyPart(i).getContent();
                if (part instanceof jakarta.mail.Multipart inner) {
                    return inner.getBodyPart(0).getContent().toString();
                }
                if (part instanceof String s) {
                    return s;
                }
            }
        }
        return String.valueOf(content);
    }
}
