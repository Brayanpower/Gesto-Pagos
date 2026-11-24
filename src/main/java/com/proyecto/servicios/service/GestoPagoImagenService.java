package com.proyecto.servicios.service;

import org.springframework.http.MediaType;

import java.util.Optional;

public interface GestoPagoImagenService {

    record ImagenRecurso(byte[] contenido, MediaType mediaType) {
    }

    Optional<ImagenRecurso> obtenerLogoServicio(String idServicio);

    Optional<ImagenRecurso> obtenerImagenReferencia(String idServicio);
}

