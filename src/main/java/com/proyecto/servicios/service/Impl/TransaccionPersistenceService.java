package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.gestopago.TransaccionServicio;
import com.proyecto.servicios.exception.CuentaNoOperativaException;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.exception.SaldoInsuficienteException;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
import com.proyecto.servicios.security.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TransaccionPersistenceService {

    private static final DateTimeFormatter HORA_LOCAL_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CuentaRepository cuentaRepository;
    private final TransaccionServicioRepository transaccionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${gestopago.tx.duplicate-window-seconds:60}")
    private long duplicateWindowSeconds;

    public TransaccionPersistenceService(
            CuentaRepository cuentaRepository,
            TransaccionServicioRepository transaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TransaccionServicio> buscarPorClaveIdempotencia(String claveIdempotencia) {
        return transaccionRepository.findByClaveIdempotencia(claveIdempotencia);
    }

    /**
     * Fase 1: Bloquea la fila de la cuenta con PESSIMISTIC_WRITE, verifica titularidad,
     * estatus activo, no duplicidad y saldo suficiente, descuenta el monto preventivamente
     * y registra la transaccion en EN_PROCESO liberando la conexion antes del llamado HTTP.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransaccionServicio reservarYDebitarSaldo(
            Long clienteAutenticadoId,
            String numeroCuenta,
            String claveIdempotencia,
            CatalogoProducto producto,
            Integer idServicio,
            Integer idProducto,
            BigDecimal monto,
            String telefono,
            String referencia) {

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(numeroCuenta)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La cuenta bancaria indicada no existe."));

        if (cuenta.getCliente() == null || !cuenta.getCliente().getId().equals(clienteAutenticadoId)) {
            log.warn("Intento no autorizado de uso de cuenta {} por clienteId={}",
                    LogSanitizer.maskAccount(numeroCuenta), clienteAutenticadoId);
            throw new AccessDeniedException("No tiene autorizacion para operar con la cuenta indicada.");
        }

        if (!cuenta.getCliente().isActivo()) {
            throw new AccessDeniedException("El cliente titular no se encuentra activo para realizar operaciones.");
        }

        if (cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            throw new CuentaNoOperativaException(
                    "La cuenta bancaria no se encuentra activa (estatus: " + cuenta.getEstatus() + ").");
        }

        Optional<TransaccionServicio> existentePorClave = transaccionRepository.findByClaveIdempotencia(claveIdempotencia);
        if (existentePorClave.isPresent()) {
            throw new DuplicateResourceException(
                    "Ya existe una solicitud registrada con la misma clave de idempotencia.");
        }

        LocalDateTime ventanaDesde = LocalDateTime.now().minusSeconds(Math.max(1L, duplicateWindowSeconds));
        boolean duplicadaReciente = transaccionRepository.existeOperacionDuplicadaReciente(
                cuenta.getId(),
                idServicio,
                idProducto,
                monto,
                telefono != null ? telefono : "",
                referencia != null ? referencia : "",
                List.of(EstatusTransaccion.EN_PROCESO, EstatusTransaccion.PENDIENTE_CONCILIACION),
                EstatusTransaccion.EXITOSA,
                ventanaDesde
        );

        if (duplicadaReciente) {
            throw new DuplicateResourceException(
                    "Se detecto una operacion identica reciente o en curso para la misma cuenta. "
                            + "Por seguridad se bloqueo el cargo duplicado.");
        }

        BigDecimal saldoAnterior = cuenta.getSaldo();
        if (saldoAnterior == null || saldoAnterior.compareTo(monto) < 0) {
            throw new SaldoInsuficienteException(
                    "Saldo disponible insuficiente en la cuenta para realizar la operacion.");
        }

        BigDecimal saldoPosterior = saldoAnterior.subtract(monto);
        cuenta.setSaldo(saldoPosterior);
        cuentaRepository.save(cuenta);

        String upc = generarUpcUnico();
        String horaLocal = LocalDateTime.now().format(HORA_LOCAL_FMT);

        TransaccionServicio tx = new TransaccionServicio();
        tx.setCuenta(cuenta);
        tx.setCliente(cuenta.getCliente());
        tx.setNumeroCuenta(cuenta.getNumeroCuenta());
        tx.setClaveIdempotencia(claveIdempotencia);
        tx.setUpc(upc);
        tx.setIdServicio(idServicio);
        tx.setNombreServicio(producto.getServicio());
        tx.setIdProducto(idProducto);
        tx.setNombreProducto(producto.getNombreProducto());
        tx.setMonto(monto);
        tx.setSaldoAnterior(saldoAnterior);
        tx.setSaldoPosterior(saldoPosterior);
        tx.setTelefono(telefono);
        tx.setReferencia(referencia);
        tx.setEstatus(EstatusTransaccion.EN_PROCESO);
        tx.setHoraLocal(horaLocal);
        tx.setIntentosConciliacion(0);

        TransaccionServicio guardada = transaccionRepository.save(tx);
        log.info("Debito preventivo aplicado para cuenta={} upc={} monto={}",
                LogSanitizer.maskAccount(numeroCuenta), upc, monto);
        return guardada;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransaccionServicio confirmarExito(Long transaccionId, GestoPagoTxResult resultado) {
        TransaccionServicio tx = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaccion no encontrada: " + transaccionId));

        if (tx.getEstatus() == EstatusTransaccion.EXITOSA) {
            return tx;
        }

        tx.setEstatus(EstatusTransaccion.EXITOSA);
        tx.setCodigoRespuestaProveedor(truncar(resultado.getCodigo(), 20));
        tx.setMensajeProveedor(truncar(resultado.getMensaje(), 500));
        tx.setFolioProveedor(truncar(resultado.getIdTx(), 100));
        tx.setNumAutorizacion(truncar(resultado.getNumAutorizacion(), 100));
        return transaccionRepository.save(tx);
    }

    /**
     * Compensa y devuelve el saldo a la cuenta del cliente unicamente cuando existe certeza
     * de que el proveedor rechazo o no recibio la operacion. Garantiza idempotencia del reverso.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransaccionServicio reversarCargo(
            Long transaccionId,
            EstatusTransaccion estatusFinal,
            String codigoProveedor,
            String mensajeProveedor) {

        TransaccionServicio tx = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaccion no encontrada: " + transaccionId));

        if (tx.getEstatus() == EstatusTransaccion.RECHAZADA
                || tx.getEstatus() == EstatusTransaccion.REVERSADA
                || tx.getEstatus() == EstatusTransaccion.EXITOSA) {
            return tx;
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(tx.getNumeroCuenta())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta asociada no encontrada para reverso"));

        BigDecimal saldoRestaurado = cuenta.getSaldo().add(tx.getMonto());
        cuenta.setSaldo(saldoRestaurado);
        cuentaRepository.save(cuenta);

        tx.setSaldoPosterior(saldoRestaurado);
        tx.setEstatus(estatusFinal);
        tx.setCodigoRespuestaProveedor(truncar(codigoProveedor, 20));
        tx.setMensajeProveedor(truncar(mensajeProveedor, 500));

        TransaccionServicio guardada = transaccionRepository.save(tx);
        log.info("Saldo reversado integramente en cuenta={} por upc={} estatus={}",
                LogSanitizer.maskAccount(tx.getNumeroCuenta()), tx.getUpc(), estatusFinal);
        return guardada;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransaccionServicio marcarPendienteConciliacion(
            Long transaccionId,
            String codigoProveedor,
            String mensajeProveedor,
            boolean incrementarIntento) {

        TransaccionServicio tx = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaccion no encontrada: " + transaccionId));

        if (tx.getEstatus() == EstatusTransaccion.EXITOSA
                || tx.getEstatus() == EstatusTransaccion.RECHAZADA
                || tx.getEstatus() == EstatusTransaccion.REVERSADA) {
            return tx;
        }

        tx.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
        tx.setCodigoRespuestaProveedor(truncar(codigoProveedor, 20));
        tx.setMensajeProveedor(truncar(mensajeProveedor, 500));
        if (incrementarIntento) {
            int actual = tx.getIntentosConciliacion() != null ? tx.getIntentosConciliacion() : 0;
            tx.setIntentosConciliacion(actual + 1);
        }
        return transaccionRepository.save(tx);
    }

    private String generarUpcUnico() {
        String upc;
        do {
            long numero = 1_000_000_000L + (long) (secureRandom.nextDouble() * 9_000_000_000L);
            upc = String.valueOf(numero);
        } while (transaccionRepository.existsByUpc(upc));
        return upc;
    }

    private static String truncar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}

