package com.proyecto.servicios.service;

import com.proyecto.servicios.model.seguridad.AuthResponse;
import com.proyecto.servicios.model.seguridad.LoginRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);
}
