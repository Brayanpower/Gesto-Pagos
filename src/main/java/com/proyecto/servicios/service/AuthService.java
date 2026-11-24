package com.proyecto.servicios.service;

import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.seguridad.AuditoriaLoginResponse;
import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.ClientMetadata;
import com.proyecto.servicios.model.seguridad.LoginRequest;
import com.proyecto.servicios.model.seguridad.RefreshTokenRequest;
import com.proyecto.servicios.model.seguridad.TotpVerificationRequest;
import org.springframework.data.domain.Pageable;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse login(LoginRequest request, ClientMetadata metadata);

    AuthResponse verificarTotp(TotpVerificationRequest request, ClientMetadata metadata);

    AuthResponse refresh(RefreshTokenRequest request, ClientMetadata metadata);

    void logout(String authorizationHeader, RefreshTokenRequest request, ClientMetadata metadata);

    void logoutAll(ClientMetadata metadata);

    PageResponse<AuditoriaLoginResponse> consultarAuditoria(String correo, Pageable pageable);
}
