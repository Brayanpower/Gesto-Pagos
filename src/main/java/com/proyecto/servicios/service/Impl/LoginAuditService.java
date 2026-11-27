package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.seguridad.AuditoriaLogin;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.repositorys.seguridad.AuditoriaLoginRepository;
import com.proyecto.servicios.security.LogSanitizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginAuditService {

    public static final String EVENTO_EXITO = "EXITO";
    public static final String EVENTO_PASSWORD_INVALIDA = "PASSWORD_INVALIDA";
    public static final String EVENTO_CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    public static final String EVENTO_USUARIO_INACTIVO = "USUARIO_INACTIVO";
    public static final String EVENTO_TOTP_CONFIG_REQUERIDA = "TOTP_CONFIG_REQUERIDA";
    public static final String EVENTO_TOTP_REQUERIDO = "TOTP_REQUERIDO";
    public static final String EVENTO_TOTP_INVALIDO = "TOTP_INVALIDO";
    public static final String EVENTO_REFRESH_EXITO = "REFRESH_EXITO";
    public static final String EVENTO_REFRESH_REUSO = "REFRESH_REUSO_DETECTADO";
    public static final String EVENTO_LOGOUT = "LOGOUT";
    public static final String EVENTO_LOGOUT_ALL = "LOGOUT_ALL";
    public static final String EVENTO_SESION_CONFLICTO = "SESION_ACTIVA_CONFLICTO";
    public static final String EVENTO_SESION_FORZADA = "SESION_ANTERIOR_CERRADA";
    public static final String EVENTO_TOTP_RECUPERACION_EXITO = "TOTP_RECUPERACION_EXITO";
    public static final String EVENTO_TOTP_RECUPERACION_FALLIDA = "TOTP_RECUPERACION_FALLIDA";
    public static final String EVENTO_TOTP_RESET_ADMIN = "TOTP_RESET_ADMIN";
    public static final String EVENTO_OTP_CORREO_ENVIADO = "OTP_CORREO_ENVIADO";
    public static final String EVENTO_TOTP_RECUPERACION_CORREO_SOLICITADA = "TOTP_RECUPERACION_CORREO_SOLICITADA";

    private static final int MAX_CORREO_LENGTH = 100;
    private static final int MAX_EVENTO_LENGTH = 40;
    private static final int MAX_DETALLE_LENGTH = 255;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String VALOR_DESCONOCIDO = "desconocido";
    private static final String EVENTO_DESCONOCIDO = "DESCONOCIDO";

    private final AuditoriaLoginRepository auditoriaLoginRepository;

    public LoginAuditService(AuditoriaLoginRepository auditoriaLoginRepository) {
        this.auditoriaLoginRepository = auditoriaLoginRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEvento(
            Usuario usuario,
            String correo,
            String evento,
            boolean exitoso,
            ClientMetadata metadata,
            String detalle) {
        ClientMetadata meta = metadata != null ? metadata : ClientMetadata.desconocido();

        AuditoriaLogin registro = new AuditoriaLogin();
        registro.setUsuario(usuario);
        registro.setCorreo(normalizarCorreo(usuario, correo));
        registro.setEvento(normalizarEvento(evento));
        registro.setExitoso(exitoso);
        registro.setIpOrigen(meta.ipOrigen());
        registro.setUserAgent(meta.userAgent());
        registro.setDetalle(normalizarDetalle(detalle));

        auditoriaLoginRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditoriaLoginResponse> consultarAuditoria(String correo, Pageable pageable) {
        Pageable pageableValido = validarYNormalizarPageable(pageable);

        Page<AuditoriaLogin> pagina;
        if (correo != null && !correo.isBlank()) {
            String correoFiltro = limpiarTexto(correo).toLowerCase(Locale.ROOT);
            pagina = auditoriaLoginRepository.findByCorreoOrderByFechaCreacionDesc(correoFiltro, pageableValido);
        } else {
            pagina = auditoriaLoginRepository.findAllByOrderByFechaCreacionDesc(pageableValido);
        }

        return PageResponse.from(pagina.map(this::aResponse));
    }

    private Pageable validarYNormalizarPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReglaNegocioException("El tamano de pagina debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, pageable.getSort());
    }

    private AuditoriaLoginResponse aResponse(AuditoriaLogin entity) {
        Long usuarioId = entity.getUsuario() != null ? entity.getUsuario().getId() : null;
        return AuditoriaLoginResponse.builder()
                .id(entity.getId())
                .usuarioId(usuarioId)
                .correo(entity.getCorreo())
                .evento(entity.getEvento())
                .exitoso(entity.isExitoso())
                .ipOrigen(entity.getIpOrigen())
                .userAgent(entity.getUserAgent())
                .detalle(entity.getDetalle())
                .fechaCreacion(entity.getFechaCreacion())
                .build();
    }

    private String normalizarCorreo(Usuario usuario, String correo) {
        String fuente = (correo != null && !correo.isBlank()) ? correo : resolverCorreoUsuario(usuario);
        if (fuente == null || fuente.isBlank()) {
            return VALOR_DESCONOCIDO;
        }
        String limpio = limpiarTexto(fuente).toLowerCase(Locale.ROOT);
        if (limpio.isEmpty()) {
            return VALOR_DESCONOCIDO;
        }
        return recortar(limpio, MAX_CORREO_LENGTH);
    }

    private String resolverCorreoUsuario(Usuario usuario) {
        return usuario != null ? usuario.getCorreo() : null;
    }

    private String normalizarEvento(String evento) {
        if (evento == null || evento.isBlank()) {
            return EVENTO_DESCONOCIDO;
        }
        String limpio = limpiarTexto(evento);
        return limpio.isEmpty() ? EVENTO_DESCONOCIDO : recortar(limpio, MAX_EVENTO_LENGTH);
    }

    private String normalizarDetalle(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return null;
        }
        String sanitizado = LogSanitizer.sanitize(limpiarTexto(detalle));
        if (sanitizado == null || sanitizado.isBlank()) {
            return null;
        }
        return recortar(sanitizado, MAX_DETALLE_LENGTH);
    }

    private String limpiarTexto(String valor) {
        return valor.replace("\r", " ").replace("\n", " ").trim();
    }

    private String recortar(String valor, int maxLength) {
        return valor.length() > maxLength ? valor.substring(0, maxLength) : valor;
    }
}
