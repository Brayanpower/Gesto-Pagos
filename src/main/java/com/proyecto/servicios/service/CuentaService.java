package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaCreacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CuentaService {

    CuentaResponse crearCuenta(CuentaCreacionRequest request);

    CuentaResponse obtenerPorNumeroCuenta(String numeroCuenta);

    List<CuentaResponse> obtenerPorClienteId(Long clienteId);

    List<CuentaResponse> obtenerPorEstatus(EstatusCuenta estatus);

    List<CuentaResponse> listarTodas();

    CuentaResponse actualizarEstatus(String numeroCuenta, CuentaActualizacionRequest request);

    BigDecimal consultarSaldo(String numeroCuenta);
}
