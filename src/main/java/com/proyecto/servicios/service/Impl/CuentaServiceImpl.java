package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.mapper.CuentaMapper;
import com.proyecto.servicios.model.banco.CuentaActualizacionRequest;
import com.proyecto.servicios.model.banco.CuentaResponse;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
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
    private final TransaccionServicioRepository transaccionServicioRepository;
    private final EmailNotificationService emailNotificationService;

    public CuentaServiceImpl(
            CuentaRepository cuentaRepository,
            CuentaMapper cuentaMapper,
            AuthenticatedUserService authenticatedUserService,
            TransaccionServicioRepository transaccionServicioRepository,
            EmailNotificationService emailNotificationService) {
        this.cuentaRepository = cuentaRepository;
        this.cuentaMapper = cuentaMapper;
        this.authenticatedUserService = authenticatedUserService;
        this.transaccionServicioRepository = transaccionServicioRepository;
        this.emailNotificationService = emailNotificationService;
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

        if (request.getEstatus() == EstatusCuenta.RECHAZADA) {
            if (cuenta.getEstatus() != EstatusCuenta.PENDIENTE_REVISION) {
                throw new ReglaNegocioException(
                        "Solo se puede rechazar una cuenta que se encuentre en estatus PENDIENTE_REVISION.");
            }
            if (request.getObservacionRevision() == null || request.getObservacionRevision().isBlank()) {
                throw new ReglaNegocioException(
                        "Debe indicar una observacion o motivo al rechazar una cuenta de onboarding.");
            }
        }

        if (request.getEstatus() == EstatusCuenta.CANCELADA
                && transaccionServicioRepository.existsByCuentaIdAndEstatusIn(
                        cuenta.getId(),
                        List.of(EstatusTransaccion.EN_PROCESO, EstatusTransaccion.PENDIENTE_CONCILIACION))) {
            throw new ReglaNegocioException(
                    "No es posible cancelar una cuenta con transacciones en proceso o pendientes de conciliacion.");
        }

        if (request.getEstatus() == EstatusCuenta.ACTIVA
                && (cuenta.getCliente() == null || !cuenta.getCliente().isActivo())) {
            throw new ReglaNegocioException(
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
        if (emailNotificationService != null
                && actualizada.getCliente() != null
                && actualizada.getCliente().getCorreo() != null
                && !actualizada.getCliente().getCorreo().isBlank()) {
            String nombreCompleto = ((actualizada.getCliente().getNombre() != null ? actualizada.getCliente().getNombre() : "")
                    + " " + (actualizada.getCliente().getApellidoPaterno() != null ? actualizada.getCliente().getApellidoPaterno() : "")).trim();
            emailNotificationService.enviarNotificacionDictamenCuenta(
                    actualizada.getCliente().getCorreo(),
                    nombreCompleto,
                    actualizada.getNumeroCuenta(),
                    actualizada.getEstatus(),
                    actualizada.getObservacionRevision());
        }
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