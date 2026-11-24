package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.CuentaMapper;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import com.proyecto.servicios.service.CuentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class CuentaServiceImpl implements CuentaService {

    private final CuentaRepository cuentaRepository;
    private final CuentaMapper cuentaMapper;
    private final AuthenticatedUserService authenticatedUserService;

    public CuentaServiceImpl(
            CuentaRepository cuentaRepository,
            CuentaMapper cuentaMapper,
            AuthenticatedUserService authenticatedUserService) {
        this.cuentaRepository = cuentaRepository;
        this.cuentaMapper = cuentaMapper;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse obtenerMiCuenta() {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esCliente(usuario) || usuario.getCliente() == null) {
            throw new AccessDeniedException("Solo un cliente tiene una cuenta de ahorro asociada.");
        }
        Cuenta cuenta = cuentaRepository.findByClienteId(usuario.getCliente().getId())
                .orElseThrow(() -> new ResourceNotFoundException("El cliente no tiene una cuenta de ahorro registrada."));
        return cuentaMapper.toResponse(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse obtenerPorNumeroCuenta(String numeroCuenta) {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        Cuenta cuenta = buscarConCliente(numeroCuenta);
        validarAccesoCuenta(usuario, cuenta);
        return cuentaMapper.toResponse(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerPorClienteId(Long clienteId) {
        exigirRolAdmin("Solo un administrador puede consultar cuentas por clienteId.");
        return cuentaRepository.findByClienteId(clienteId)
                .map(cuenta -> List.of(cuentaMapper.toResponse(cuenta)))
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerPorEstatus(EstatusCuenta estatus) {
        exigirRolAdmin("Solo un administrador puede filtrar todas las cuentas por estatus.");
        return cuentaMapper.toResponses(cuentaRepository.findByEstatus(estatus));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerPendientesRevision() {
        exigirRolAdmin("Solo un administrador puede consultar la bandeja de cuentas de onboarding.");
        return cuentaMapper.toResponses(
                cuentaRepository.findByEstatusOrderByFechaCreacionAsc(EstatusCuenta.PENDIENTE_REVISION));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> listarTodas() {
        exigirRolAdmin("Solo un administrador puede listar todas las cuentas bancarias.");
        return cuentaMapper.toResponses(cuentaRepository.findAll());
    }

    @Override
    @Transactional
    public CuentaResponse revisarCuenta(String numeroCuenta, CuentaActualizacionRequest request) {
        Usuario admin = exigirRolAdmin("Solo un administrador puede revisar y dictaminar cuentas de onboarding.");
        log.info("Revision de cuenta {} a estatus {} por admin {}", numeroCuenta, request.getEstatus(), admin.getCorreo());

        if (request.getEstatus() == EstatusCuenta.PENDIENTE_REVISION) {
            throw new ReglaNegocioException("No se puede asignar manualmente el estatus PENDIENTE_REVISION.");
        }

        Cuenta cuenta = buscarConCliente(numeroCuenta);
        if (cuenta.getEstatus() == EstatusCuenta.CANCELADA && request.getEstatus() != EstatusCuenta.CANCELADA) {
            throw new ReglaNegocioException("Una cuenta cancelada no puede cambiar de estatus.");
        }

        if (request.getEstatus() == EstatusCuenta.ACTIVA
                && (cuenta.getCliente() == null || !cuenta.getCliente().isActivo())) {
            throw new IllegalArgumentException(
                    "No se puede activar la cuenta porque el cliente titular no esta activo");
        }

        cuenta.setEstatus(request.getEstatus());
        cuenta.setRevisadoPor(admin.getCorreo());
        if (request.getObservacionRevision() != null) {
            String obs = request.getObservacionRevision().trim();
            cuenta.setObservacionRevision(obs.isEmpty() ? null : obs);
        }
        cuenta.setFechaRevision(LocalDateTime.now(ZoneId.systemDefault()));

        Cuenta actualizada = cuentaRepository.save(cuenta);
        return cuentaMapper.toResponse(actualizada);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldo(String numeroCuenta) {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        Cuenta cuenta = buscarConCliente(numeroCuenta);
        validarAccesoCuenta(usuario, cuenta);
        return cuenta.getSaldo();
    }

    private Cuenta buscarConCliente(String numeroCuenta) {
        return cuentaRepository.findWithClienteByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro la cuenta bancaria: " + numeroCuenta));
    }

    private Usuario exigirRolAdmin(String mensajeError) {
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esAdmin(usuario)) {
            throw new AccessDeniedException(mensajeError);
        }
        return usuario;
    }

    private void validarAccesoCuenta(Usuario usuario, Cuenta cuenta) {
        if (authenticatedUserService.esAdmin(usuario)) {
            return;
        }
        if (usuario.getCliente() == null
                || cuenta.getCliente() == null
                || !usuario.getCliente().getId().equals(cuenta.getCliente().getId())) {
            throw new AccessDeniedException("No tiene autorizacion para consultar una cuenta que no le pertenece.");
        }
    }
}