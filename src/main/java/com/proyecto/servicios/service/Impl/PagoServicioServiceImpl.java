package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.cuentas.Cuenta;
import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import com.proyecto.servicios.entity.gestopago.EstatusTransaccion;
import com.proyecto.servicios.entity.gestopago.TransaccionServicio;
import com.proyecto.servicios.entity.seguridad.Usuario;
import com.proyecto.servicios.exception.DuplicateResourceException;
import com.proyecto.servicios.exception.OperacionRechazadaProveedorException;
import com.proyecto.servicios.exception.ProveedorExternoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.exception.ResourceNotFoundException;
import com.proyecto.servicios.exception.TransaccionTimeoutConciliacionException;
import com.proyecto.servicios.model.PageResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.GestoPagoTxResult.EstadoProveedor;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.HistorialCompraResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.PagoServicioResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.ReservaSaldoCommand;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTx;
import com.proyecto.servicios.model.gestopago.GestoPagoSendTx.SendTxRequest;
import com.proyecto.servicios.repositorys.cuentas.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.TransaccionServicioRepository;
import com.proyecto.servicios.security.AuthenticatedUserService;
import com.proyecto.servicios.service.CatalogoService;
import com.proyecto.servicios.service.GestoPagoSendTxService;
import com.proyecto.servicios.service.PagoServicioService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PagoServicioServiceImpl implements PagoServicioService {

    private static final String CAMPO_FECHA_CREACION = "fechaCreacion";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> CAMPOS_ORDENAMIENTO_PERMITIDOS = Set.of(
            CAMPO_FECHA_CREACION, "monto", "estatus", "idServicio", "idProducto"
    );
    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{8,100}$");
    private static final Pattern TELEFONO_10_DIGITOS = Pattern.compile("^\\d{10}$");
    private static final Pattern PATTERN_UPC = Pattern.compile("^\\d{3,15}$");

    private final CatalogoService catalogoService;
    private final TransaccionPersistenceService persistenceService;
    private final GestoPagoSendTxService gestoPagoSendTxService;
    private final TransaccionServicioRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Value("${gestopago.tx.unidad:GPS83-TPV-17}")
    private String unidadDistribuidor;

    @Value("${gestopago.tx.monto-minimo:1.00}")
    private BigDecimal montoMinimoPermitido;

    @Value("${gestopago.tx.monto-maximo:50000.00}")
    private BigDecimal montoMaximoPermitido;

    @Value("${gestopago.tx.conciliacion-inmediata:true}")
    private boolean conciliacionInmediata;

    @Value("${gestopago.tx.conciliacion.max-intentos:5}")
    private int maxIntentosConciliacion;

    public PagoServicioServiceImpl(
            CatalogoService catalogoService,
            TransaccionPersistenceService persistenceService,
            GestoPagoSendTxService gestoPagoSendTxService,
            TransaccionServicioRepository transaccionRepository,
            CuentaRepository cuentaRepository,
            AuthenticatedUserService authenticatedUserService) {
        this.catalogoService = catalogoService;
        this.persistenceService = persistenceService;
        this.gestoPagoSendTxService = gestoPagoSendTxService;
        this.transaccionRepository = transaccionRepository;
        this.cuentaRepository = cuentaRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Override
    public PagoServicioResponse procesarPagoServicio(SendTxRequest request, String headerIdempotencyKey) {
        Usuario usuarioAutenticado = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esCliente(usuarioAutenticado)) {
            throw new AccessDeniedException("Solo un cliente con cuenta de ahorro puede realizar compras de servicios.");
        }
        Cliente cliente = validarYObtenerClienteActivo(usuarioAutenticado);

        String numeroCuenta = resolverNumeroCuentaAhorro(request.getNumeroCuenta(), cliente.getId());
        String claveIdempotencia = resolverYValidarClaveIdempotencia(request.getClaveIdempotencia(), headerIdempotencyKey);
        boolean tieneClaveExplicita = (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
                || (request.getClaveIdempotencia() != null && !request.getClaveIdempotencia().isBlank());

        // Evaluacion temprana de idempotencia si el cliente envio clave explicita
        if (tieneClaveExplicita) {
            Optional<TransaccionServicio> previaOpt = persistenceService.buscarPorClaveIdempotencia(claveIdempotencia);
            if (previaOpt.isPresent()) {
                return resolverRespuestaIdempotente(previaOpt.get(), cliente.getId(), numeroCuenta, request);
            }
        }

        // Validacion de catalogo, producto, monto y parametros de operacion
        CatalogoProducto productoCatalogo = catalogoService.validarYObtenerProducto(
                request.getIdServicio(), request.getIdProducto());

        validarMontoContraCatalogo(request.getMontoPago(), productoCatalogo);
        String telefonoEfectivo = validarYResolverTelefono(request.getTelefono(), productoCatalogo, cliente);
        String referenciaEfectiva = validarYResolverReferencia(request.getReferencia(), productoCatalogo);

        // Fase 1: Reserva / debito preventivo con bloqueo pesimista de cuenta
        TransaccionServicio tx = persistenceService.reservarYDebitarSaldo(new ReservaSaldoCommand(
                cliente.getId(),
                numeroCuenta,
                claveIdempotencia,
                productoCatalogo,
                request.getIdServicio(),
                request.getIdProducto(),
                request.getMontoPago(),
                telefonoEfectivo,
                referenciaEfectiva
        ));

        // Fase 2: Envio hacia GestoPago sendTx.do (fuera de transaccion de BD)
        SendTx payloadProveedor = construirPayloadProveedor(tx);
        GestoPagoTxResult resultadoProveedor;
        try {
            resultadoProveedor = gestoPagoSendTxService.ejecutarTransaccion(payloadProveedor);
        } catch (ProveedorExternoException ex) {
            // Fallo previo al envio o rechazo HTTP antes de procesar: reversamos el saldo de forma segura
            persistenceService.reversarCargo(
                    tx.getId(),
                    EstatusTransaccion.RECHAZADA,
                    "FALLO_PREVIO",
                    ex.getMessage()
            );
            throw ex;
        }

        // Fase 3: Tratamiento del resultado y conciliacion ante estados indeterminados
        return resolverResultadoOperacion(tx, resultadoProveedor);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoServicioResponse consultarTransaccionPorUpc(String upc) {
        if (upc == null || !PATTERN_UPC.matcher(upc.trim()).matches()) {
            throw new ReglaNegocioException("El folio UPC proporcionado no tiene un formato valido.");
        }
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        TransaccionServicio tx = transaccionRepository.findByUpc(upc.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro ninguna transaccion con folio UPC: " + upc));

        validarAccesoTransaccion(usuario, tx);
        return aPagoServicioResponse(tx, false);
    }

    @Override
    public PagoServicioResponse conciliarTransaccion(String upc) {
        if (upc == null || !PATTERN_UPC.matcher(upc.trim()).matches()) {
            throw new ReglaNegocioException("El folio UPC proporcionado no tiene un formato valido.");
        }
        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        TransaccionServicio tx = transaccionRepository.findByUpc(upc.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro ninguna transaccion con folio UPC: " + upc));

        validarAccesoTransaccion(usuario, tx);

        if (tx.getEstatus() != EstatusTransaccion.PENDIENTE_CONCILIACION) {
            return aPagoServicioResponse(tx, false);
        }

        TransaccionServicio conciliada = ejecutarConciliacionIndividual(tx);
        return aPagoServicioResponse(conciliada, false);
    }

    @Scheduled(cron = "${gestopago.tx.conciliacion.cron:0 */5 * * * *}")
    public void ejecutarJobConciliacionPendientes() {
        List<TransaccionServicio> pendientes = transaccionRepository
                .findTop20ByEstatusAndIntentosConciliacionLessThanOrderByFechaCreacionAsc(
                        EstatusTransaccion.PENDIENTE_CONCILIACION, maxIntentosConciliacion);

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Iniciando proceso programado de conciliacion para {} transacciones pendientes", pendientes.size());
        for (TransaccionServicio tx : pendientes) {
            try {
                ejecutarConciliacionIndividual(tx);
            } catch (Exception ex) {
                log.warn("Error al conciliar transaccion upc={}: {}", tx.getUpc(), ex.getClass().getSimpleName());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistorialCompraResponse> obtenerHistorialCompras(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String numeroCuenta,
            EstatusTransaccion estatus,
            Pageable pageable) {

        Usuario usuario = authenticatedUserService.obtenerUsuarioAutenticadoActivo();
        if (!authenticatedUserService.esCliente(usuario)) {
            throw new AccessDeniedException("Solo un cliente puede consultar su historial de compras.");
        }
        Cliente cliente = validarYObtenerClienteActivo(usuario);

        validarRangoFechasHistorial(fechaInicio, fechaFin);
        String cuentaFiltrada = validarTitularidadCuentaHistorial(numeroCuenta, cliente);
        Pageable pageableAjustado = normalizarPaginacion(pageable);

        Specification<TransaccionServicio> spec = construirSpecificationHistorial(
                cliente.getId(), cuentaFiltrada, estatus, fechaInicio, fechaFin);

        Page<HistorialCompraResponse> resultadoPaginado = transaccionRepository
                .findAll(spec, pageableAjustado)
                .map(this::aHistorialCompraResponse);

        return PageResponse.from(resultadoPaginado);
    }

    private void validarRangoFechasHistorial(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ReglaNegocioException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
    }

    private String validarTitularidadCuentaHistorial(String numeroCuenta, Cliente cliente) {
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            return null;
        }
        String cuentaFiltrada = numeroCuenta.trim();
        Cuenta cuenta = cuentaRepository.findWithClienteByNumeroCuenta(cuentaFiltrada)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta bancaria indicada no existe."));
        if (cuenta.getCliente() == null || !cuenta.getCliente().getId().equals(cliente.getId())) {
            throw new AccessDeniedException("No tiene autorizacion para consultar el historial de esta cuenta.");
        }
        return cuentaFiltrada;
    }

    private Pageable normalizarPaginacion(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, CAMPO_FECHA_CREACION));
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReglaNegocioException("El tamano de pagina debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, CAMPO_FECHA_CREACION);
        } else {
            List<Sort.Order> ordenesValidas = sort.stream()
                    .filter(order -> CAMPOS_ORDENAMIENTO_PERMITIDOS.contains(order.getProperty()))
                    .toList();
            sort = ordenesValidas.isEmpty()
                    ? Sort.by(Sort.Direction.DESC, CAMPO_FECHA_CREACION)
                    : Sort.by(ordenesValidas);
        }
        return PageRequest.of(page, size, sort);
    }

    private Specification<TransaccionServicio> construirSpecificationHistorial(
            Long clienteId, String cuenta, EstatusTransaccion estatus, LocalDate fechaInicio, LocalDate fechaFin) {
        LocalDateTime desde = fechaInicio != null ? fechaInicio.atStartOfDay() : null;
        LocalDateTime hastaExclusivo = fechaFin != null ? fechaFin.plusDays(1).atStartOfDay() : null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("cliente").get("id"), clienteId));

            if (cuenta != null) {
                predicates.add(cb.equal(root.get("numeroCuenta"), cuenta));
            }
            if (estatus != null) {
                predicates.add(cb.equal(root.get("estatus"), estatus));
            }
            if (desde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(CAMPO_FECHA_CREACION), desde));
            }
            if (hastaExclusivo != null) {
                predicates.add(cb.lessThan(root.get(CAMPO_FECHA_CREACION), hastaExclusivo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PagoServicioResponse resolverResultadoOperacion(
            TransaccionServicio tx,
            GestoPagoTxResult resultado) {

        if (resultado.getEstado() == EstadoProveedor.APROBADA) {
            TransaccionServicio confirmada = persistenceService.confirmarExito(tx.getId(), resultado);
            return aPagoServicioResponse(confirmada, false);
        }

        if (resultado.getEstado() == EstadoProveedor.RECHAZADA_PROVEEDOR) {
            persistenceService.reversarCargo(
                    tx.getId(),
                    EstatusTransaccion.RECHAZADA,
                    resultado.getCodigo(),
                    resultado.getMensaje()
            );
            throw new OperacionRechazadaProveedorException(
                    "Operacion rechazada por el proveedor de servicios: " + resultado.getMensaje()
                            + ". El saldo de su cuenta fue restituido integramente.");
        }

        // Caso TIMEOUT o estado indeterminado: NO reintentar sendTx.do ni asumir que fallo
        TransaccionServicio pendiente = persistenceService.marcarPendienteConciliacion(
                tx.getId(), resultado.getCodigo(), resultado.getMensaje(), false);

        if (conciliacionInmediata) {
            TransaccionServicio verificada = ejecutarConciliacionIndividual(pendiente);
            if (verificada.getEstatus() == EstatusTransaccion.EXITOSA) {
                return aPagoServicioResponse(verificada, false);
            }
            if (verificada.getEstatus() == EstatusTransaccion.REVERSADA) {
                throw new OperacionRechazadaProveedorException(
                        "Tras conciliar con el proveedor se confirmo que la operacion no fue aplicada. "
                                + "El saldo de su cuenta fue restituido integramente.");
            }
        }

        throw new TransaccionTimeoutConciliacionException(
                tx.getUpc(),
                "Tiempo de espera agotado o estado en validacion con el proveedor externo. "
                        + "Para evitar cargos duplicados no se reintento la operacion; el folio "
                        + tx.getUpc() + " se encuentra en proceso de conciliacion."
        );
    }

    private TransaccionServicio ejecutarConciliacionIndividual(TransaccionServicio tx) {
        SendTx consultaPayload = construirPayloadProveedor(tx);
        GestoPagoTxResult verificacion = gestoPagoSendTxService.verificarTransaccion(consultaPayload);

        if (verificacion.getEstado() == EstadoProveedor.APROBADA) {
            log.info("Conciliacion exitosa: proveedor confirmo aplicacion para upc={}", tx.getUpc());
            return persistenceService.confirmarExito(tx.getId(), verificacion);
        }

        if (verificacion.getEstado() == EstadoProveedor.RECHAZADA_PROVEEDOR) {
            log.info("Conciliacion resuelta: proveedor confirmo rechazo/inexistencia para upc={}; se reversa saldo", tx.getUpc());
            return persistenceService.reversarCargo(
                    tx.getId(),
                    EstatusTransaccion.REVERSADA,
                    verificacion.getCodigo(),
                    "Reverso aplicado por conciliacion: " + verificacion.getMensaje()
            );
        }

        return persistenceService.marcarPendienteConciliacion(
                tx.getId(),
                verificacion.getCodigo(),
                verificacion.getMensaje(),
                true
        );
    }

    private void validarMontoContraCatalogo(BigDecimal montoSolicitado, CatalogoProducto producto) {
        if (montoSolicitado == null || montoSolicitado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioException("El monto de la operacion debe ser mayor a cero.");
        }
        if (montoSolicitado.scale() > 2) {
            throw new ReglaNegocioException("El monto de la operacion no puede contener mas de 2 decimales.");
        }

        BigDecimal precioCatalogo = parsearPrecioCatalogo(producto.getPrecio());

        // Si el producto en el catalogo tiene un precio fijo (> 0), exigir coincidencia exacta
        if (precioCatalogo.compareTo(BigDecimal.ZERO) > 0) {
            if (montoSolicitado.compareTo(precioCatalogo) != 0) {
                throw new ReglaNegocioException(
                        "El monto solicitado ($" + montoSolicitado.setScale(2, RoundingMode.HALF_UP)
                                + ") no corresponde al precio autorizado del producto '"
                                + producto.getNombreProducto() + "' ($"
                                + precioCatalogo.setScale(2, RoundingMode.HALF_UP) + ").");
            }
            return;
        }

        // Producto de monto abierto/variable (ej. pago de servicios con precio 0 en catalogo)
        if (montoSolicitado.compareTo(montoMinimoPermitido) < 0
                || montoSolicitado.compareTo(montoMaximoPermitido) > 0) {
            throw new ReglaNegocioException(
                    "El monto solicitado esta fuera del rango permitido para este servicio ($"
                            + montoMinimoPermitido.setScale(2, RoundingMode.HALF_UP) + " - $"
                            + montoMaximoPermitido.setScale(2, RoundingMode.HALF_UP) + ").");
        }
    }

    private BigDecimal parsearPrecioCatalogo(String precioRaw) {
        if (precioRaw == null || precioRaw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal valor = new BigDecimal(precioRaw.trim());
            return valor.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : valor;
        } catch (NumberFormatException ex) {
            throw new ReglaNegocioException("La configuracion de precio del producto en el catalogo no es valida.");
        }
    }

    private String validarYResolverTelefono(String telefonoRequest, CatalogoProducto producto, Cliente cliente) {
        boolean esPagoServicioConReferencia = "2".equals(producto.getTipoFront());
        if (telefonoRequest == null || telefonoRequest.isBlank()) {
            if (esPagoServicioConReferencia && cliente.getTelefonoMovil() != null
                    && TELEFONO_10_DIGITOS.matcher(cliente.getTelefonoMovil().trim()).matches()) {
                return cliente.getTelefonoMovil().trim();
            }
            throw new ReglaNegocioException("El numero telefonico de 10 digitos es obligatorio para este producto.");
        }

        String limpio = telefonoRequest.trim();
        if (!esPagoServicioConReferencia && !TELEFONO_10_DIGITOS.matcher(limpio).matches()) {
            throw new ReglaNegocioException("Para recargas telefonicas el numero destino debe tener exactamente 10 digitos.");
        }
        return limpio;
    }

    private String validarYResolverReferencia(String referenciaRequest, CatalogoProducto producto) {
        boolean requiereReferencia = "2".equals(producto.getTipoFront());
        String limpia = referenciaRequest != null ? referenciaRequest.trim() : null;
        if (limpia != null && limpia.isEmpty()) {
            limpia = null;
        }
        if (requiereReferencia && (limpia == null || limpia.length() < 4)) {
            throw new ReglaNegocioException(
                    "El servicio '" + producto.getNombreProducto() + "' requiere una referencia de pago valida (minimo 4 caracteres).");
        }
        return limpia;
    }

    private String resolverYValidarClaveIdempotencia(String bodyKey, String headerKey) {
        String limpiaHeader = headerKey != null && !headerKey.isBlank() ? headerKey.trim() : null;
        String limpiaBody = bodyKey != null && !bodyKey.isBlank() ? bodyKey.trim() : null;

        if (limpiaHeader != null && limpiaBody != null && !limpiaHeader.equals(limpiaBody)) {
            throw new ReglaNegocioException(
                    "El header Idempotency-Key y el campo claveIdempotencia no coinciden.");
        }

        String elegida = limpiaHeader != null ? limpiaHeader : limpiaBody;
        if (elegida != null) {
            if (!IDEMPOTENCY_PATTERN.matcher(elegida).matches()) {
                throw new ReglaNegocioException(
                        "La clave de idempotencia debe tener entre 8 y 100 caracteres alfanumericos, guion o guion bajo.");
            }
            return elegida;
        }
        return UUID.randomUUID().toString();
    }

    private PagoServicioResponse resolverRespuestaIdempotente(
            TransaccionServicio existente, Long clienteAutenticadoId, String numeroCuentaEfectivo, SendTxRequest nuevoRequest) {

        if (existente.getCliente() == null || !existente.getCliente().getId().equals(clienteAutenticadoId)) {
            throw new DuplicateResourceException("La clave de idempotencia indicada no es valida para esta operacion.");
        }

        boolean mismosParametros = Objects.equals(existente.getNumeroCuenta(), numeroCuentaEfectivo)
                && Objects.equals(existente.getIdServicio(), nuevoRequest.getIdServicio())
                && Objects.equals(existente.getIdProducto(), nuevoRequest.getIdProducto())
                && existente.getMonto().compareTo(nuevoRequest.getMontoPago()) == 0;

        if (!mismosParametros) {
            throw new DuplicateResourceException(
                    "La clave de idempotencia ya fue utilizada previamente con parametros de transaccion distintos.");
        }

        if (existente.getEstatus() == EstatusTransaccion.EN_PROCESO) {
            throw new DuplicateResourceException("La solicitud con esta clave de idempotencia ya se encuentra en proceso.");
        }

        if (existente.getEstatus() == EstatusTransaccion.PENDIENTE_CONCILIACION) {
            throw new TransaccionTimeoutConciliacionException(
                    existente.getUpc(),
                    "La operacion previa con esta clave de idempotencia continua en proceso de conciliacion (folio "
                            + existente.getUpc() + ")."
            );
        }

        if (existente.getEstatus() == EstatusTransaccion.RECHAZADA
                || existente.getEstatus() == EstatusTransaccion.REVERSADA) {
            throw new DuplicateResourceException(
                    "La solicitud con esta clave de idempotencia ya fue procesada previamente con estatus "
                            + existente.getEstatus() + ". Genere una nueva clave si desea intentar nuevamente.");
        }

        return aPagoServicioResponse(existente, true);
    }

    private String resolverNumeroCuentaAhorro(String numeroCuentaRequest, Long clienteId) {
        if (numeroCuentaRequest != null && !numeroCuentaRequest.isBlank()) {
            return numeroCuentaRequest.trim();
        }
        Cuenta cuentaUnica = cuentaRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("El cliente no tiene una cuenta de ahorro asociada."));
        return cuentaUnica.getNumeroCuenta();
    }

    private Cliente validarYObtenerClienteActivo(Usuario usuario) {
        Cliente cliente = usuario.getCliente();
        if (cliente == null) {
            throw new AccessDeniedException("El usuario autenticado no tiene un perfil de cliente bancario asociado.");
        }
        if (!cliente.isActivo()) {
            throw new AccessDeniedException("El cliente titular se encuentra inactivo en el sistema.");
        }
        return cliente;
    }

    private void validarAccesoTransaccion(Usuario usuario, TransaccionServicio tx) {
        if (authenticatedUserService.esAdmin(usuario)) {
            return;
        }
        Cliente cliente = validarYObtenerClienteActivo(usuario);
        if (tx.getCliente() == null || !tx.getCliente().getId().equals(cliente.getId())) {
            throw new AccessDeniedException("No tiene autorizacion para consultar o conciliar esta transaccion.");
        }
    }

    private SendTx construirPayloadProveedor(TransaccionServicio tx) {
        return SendTx.builder()
                .idServicio(tx.getIdServicio())
                .idProducto(tx.getIdProducto())
                .montoPago(tx.getMonto())
                .telefono(tx.getTelefono())
                .referencia(tx.getReferencia())
                .horaLocal(tx.getHoraLocal())
                .upc(tx.getUpc())
                .unidad(unidadDistribuidor)
                .build();
    }

    private PagoServicioResponse aPagoServicioResponse(TransaccionServicio tx, boolean idempotente) {
        return PagoServicioResponse.builder()
                .transaccionId(tx.getId())
                .upc(tx.getUpc())
                .numeroCuenta(tx.getNumeroCuenta())
                .idServicio(tx.getIdServicio())
                .nombreServicio(tx.getNombreServicio())
                .idProducto(tx.getIdProducto())
                .nombreProducto(tx.getNombreProducto())
                .monto(tx.getMonto())
                .saldoDisponible(tx.getSaldoPosterior())
                .telefono(tx.getTelefono())
                .referencia(tx.getReferencia())
                .estatus(tx.getEstatus())
                .folioProveedor(tx.getFolioProveedor())
                .numAutorizacion(tx.getNumAutorizacion())
                .mensaje(tx.getMensajeProveedor())
                .fechaOperacion(tx.getFechaCreacion())
                .respuestaIdempotente(idempotente ? Boolean.TRUE : null)
                .build();
    }

    private HistorialCompraResponse aHistorialCompraResponse(TransaccionServicio tx) {
        return HistorialCompraResponse.builder()
                .id(tx.getId())
                .upc(tx.getUpc())
                .numeroCuenta(tx.getNumeroCuenta())
                .idServicio(tx.getIdServicio())
                .nombreServicio(tx.getNombreServicio())
                .idProducto(tx.getIdProducto())
                .nombreProducto(tx.getNombreProducto())
                .monto(tx.getMonto())
                .saldoAnterior(tx.getSaldoAnterior())
                .saldoPosterior(tx.getSaldoPosterior())
                .telefono(tx.getTelefono())
                .referencia(tx.getReferencia())
                .estatus(tx.getEstatus())
                .folioProveedor(tx.getFolioProveedor())
                .numAutorizacion(tx.getNumAutorizacion())
                .mensaje(tx.getMensajeProveedor())
                .fechaOperacion(tx.getFechaCreacion())
                .build();
    }
}
