package com.proyecto.servicios.service;

import com.proyecto.servicios.model.banco.ClienteRegistroRequest;
import com.proyecto.servicios.model.banco.ClienteResponse;
import com.proyecto.servicios.model.banco.ClienteUpdateRequest;

import java.util.List;

public interface ClienteService {

    ClienteResponse registrarCliente(ClienteRegistroRequest request);

    ClienteResponse obtenerPorId(Long id);

    List<ClienteResponse> buscarConFiltros(
            String nombre,
            String apellidoPaterno,
            String apellidoMaterno,
            String curp,
            String rfc,
            String correo);

    ClienteResponse actualizar(Long id, ClienteUpdateRequest request);

    void darDeBaja(Long id);
}
