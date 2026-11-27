package com.proyecto.servicios.service;

import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.model.seguridad.EmailOtpSendRequest;
import com.proyecto.servicios.model.seguridad.EmailRecoveryConfirmRequest;
import com.proyecto.servicios.model.seguridad.EmailRecoveryStartRequest;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.model.seguridad.RefreshTokenRequest;
import com.proyecto.servicios.model.seguridad.SesionActivaResponse;
import com.proyecto.servicios.model.seguridad.TotpRecoveryRequest;
import com.proyecto.servicios.model.seguridad.TotpVerificationRequest;
import org.springframework.data.domain.Pageable;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse login(LoginRequest request, ClientMetadata metadata);

    AuthResponse verificarTotp(TotpVerificationRequest request, ClientMetadata metadata);

    void enviarOtpCorreo(EmailOtpSendRequest request, ClientMetadata metadata);

    AuthResponse recuperarAccesoTotp(TotpRecoveryRequest request, ClientMetadata metadata);

    AuthResponse solicitarRecuperacionCorreo(EmailRecoveryStartRequest request, ClientMetadata metadata);

    AuthResponse confirmarRecuperacionCorreo(EmailRecoveryConfirmRequest request, ClientMetadata metadata);

    SesionActivaResponse consultarSesionActiva();

    AuthResponse refresh(RefreshTokenRequest request, ClientMetadata metadata);

    void logout(String authorizationHeader, RefreshTokenRequest request, ClientMetadata metadata);

    void logoutAll(ClientMetadata metadata);

    PageResponse<AuditoriaLoginResponse> consultarAuditoria(String correo, Pageable pageable);
}
