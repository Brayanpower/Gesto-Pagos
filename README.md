# Core Bancario + Integración GestoPago (Backend REST)

Plataforma backend empresarial desarrollada con **Spring Boot 3.5 / Java 17** que provee una API REST compartida para aplicación **Web** y aplicación **Móvil (Flutter)**, integrando onboarding bancario, autenticación segura con **JWT + 2FA TOTP obligatorio**, **exclusividad de sesión única simultánea respaldada por el servidor**, códigos de recuperación de acceso 2FA, catálogo en caché MongoDB e integración transaccional real con **GestoPago**.

---

## 1. Arquitectura y separación de responsabilidades

```text
src/main/java/com/proyecto/servicios/
├── client/        # Clientes OpenFeign hacia GestoPago (Auth, CatProduct, SendTx)
├── config/        # HikariCP/JPA, Flyway, OpenAPI, Bootstrap admin/demo, Feign timeouts
├── controller/    # Capa REST (Auth, Clientes, Cuentas, Usuarios, Catálogos, GestoPago)
├── entity/        # Entidades JPA (PostgreSQL) y documentos MongoDB (CatalogoProducto)
├── exception/     # Excepciones de negocio/seguridad y GlobalExceptionHandler centralizado
├── mapper/        # MapStruct para desacoplar entidades de DTOs
├── model/         # DTOs de entrada/salida con Bean Validation
├── repositorys/   # Repositorios JPA/Specifications y MongoDB
├── security/      # SecurityConfig (RBAC/Headers), JwtService, TotpService, RateLimiters, ClientIpResolver
└── service/       # Interfaces y capa service.Impl (lógica de negocio, MFA, sesiones, GestoPago)
```

- **Migraciones SQL reproducibles**: `src/main/resources/db/migration/V1__...` hasta `V11__mfa_email_otp.sql` administradas con **Flyway**.
- **Persistencia dual**: PostgreSQL (clientes, cuentas, usuarios, sesiones refresh, desafíos MFA, códigos de recuperación, auditoría y transacciones de servicios) + MongoDB (caché del catálogo GestoPago con actualización diaria y upsert sin ventana vacía).

---

## 2. Decisiones de seguridad y justificación técnica

1. **¿Por qué el 2FA (TOTP + recuperación) y la sesión única deben validarse en el servidor?**
   - Un atacante o cliente modificado puede omitir pantallas del frontend o alterar variables locales en el navegador/dispositivo móvil. Por ello:
     - Hasta superar el 2º factor TOTP en el backend (`POST /auth/totp/verificar` o `codigoTotp` en `/auth/login`), el servidor **no emite** `token` JWT ni `refreshToken`.
     - La **exclusividad de sesión única** verifica en servidor (`refresh_tokens`) si existe una sesión activa vigente: si el usuario ya inició sesión (ej. en móvil) e intenta ingresar desde otra plataforma (ej. web) sin confirmar, el servidor responde **`409 Conflict` (`SESION_ACTIVA_EXISTENTE`)** sin emitir tokens. Cuando el usuario confirma con `forzarCierreSesionAnterior=true`, el servidor revoca todos los `refresh_tokens` previos **e incrementa `token_version`**, invalidando de inmediato (`401`) cualquier JWT anterior en el primer dispositivo.
2. **Almacenamiento seguro de credenciales y secretos**:
   - Contraseñas con **BCrypt** y mitigación *anti-timing* (`DUMMY_BCRYPT_HASH` cuando el correo no existe).
   - Secretos TOTP y tokens Bearer de GestoPago cifrados en reposo con **`AES/GCM/NoPadding`** (`AesEncryptionImpl`), manteniendo aislado **`AES/CBC/PKCS5Padding`** (`GestoPagoPayloadCryptoImpl`) únicamente para el contrato externo `sendTx.do` / `verifyTx.do`.
   - Códigos de recuperación 2FA (`XXXX-XXXX`), tokens de desafío MFA y refresh tokens almacenados **únicamente como hash SHA-256** (nunca en texto claro).
3. **Defensa HTTP, anti-abuso e integridad transaccional**:
   - Headers HTTP estrictos (`X-Frame-Options: DENY`, `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`).
   - `LoginRateLimiter` + `PublicEndpointRateLimiter` (protegiendo login, TOTP, refresh, onboarding `POST /clientes` y proxy de imágenes).
   - `ClientIpResolver` con `AUTH_TRUST_FORWARDED_HEADERS=false` por defecto para evitar *IP spoofing*.
   - Bloqueo pesimista (`PESSIMISTIC_WRITE`) sobre la cuenta, `Idempotency-Key`, ventana anti-duplicados, defensa XXE en XML JAXB/StAX y reconciliación de transacciones ante timeouts.

---

## 3. Instalación local, ambientes y despliegue (DevOps)

### Requisitos previos
- JDK 17+, PostgreSQL, MongoDB (local o Atlas) y credenciales reales de GestoPago.

### Configuración por variables de entorno (`.env`)
1. Toma como base [application.properties.example](src/main/resources/application.properties.example) y crea un archivo `.env` en la raíz (ignorado por Git en `.gitignore`):
   - **Obligatorias**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (mínimo 32 bytes), `APP_CRYPTO_KEY` (32 bytes Base64), `APP_CRYPTO_IV` (16 bytes Base64).
   - **GestoPago**: `GESTOPAGO_URL`, `GESTOPAGO_ID_DISTRIBUIDOR`, `GESTOPAGO_CODIGO_DISPOSITIVO`, `GESTOPAGO_PASSWORD`, `GESTOPAGO_API_KEY` (o `GESTOPAGO_BEARER_TOKEN`).
   - **MongoDB**: `MONGODB_URI`.
   - **Correo SMTP y OTP por email**: `SMTP_HOST`, `SMTP_PORT` (`465`), `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`, `SMTP_SSL_ENABLE` (`true`), `SMTP_STARTTLS_ENABLE` (`false`), `MAIL_ENABLED` (`true`), `AUTH_EMAIL_OTP_MINUTES` (`5`), `AUTH_EMAIL_OTP_COOLDOWN_SECONDS` (`60`).
   - **Desarrollo local**: `SWAGGER_ENABLED=true`, `SWAGGER_PUBLIC=true`, `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080`.
2. Cargar variables y ejecutar:
   ```bash
   set -a && source .env && set +a
   ./gradlew bootRun
   ```
3. **Separación de ambientes (Producción / Render)**:
   - Mantener `SWAGGER_ENABLED=false`, `SWAGGER_PUBLIC=false`, `ADMIN_BOOTSTRAP_ENABLED=false`, `DEMO_CLIENTE_BOOTSTRAP_ENABLED=false`.
   - Detrás del proxy TLS de Render habilitar `SERVER_FORWARD_HEADERS_STRATEGY=framework` y `AUTH_TRUST_FORWARDED_HEADERS=true`.
   - Imagen de producción multi-stage Alpine con usuario no root (`USER app`) en [Dockerfile](Dockerfile).
4. **Flujo Git y CI**:
   - Ramas `feature/*` / `fix/*` → PR hacia `develop` → PR hacia `master`.
   - Pipeline en [.github/workflows/ci.yml](.github/workflows/ci.yml) ejecuta `./gradlew test jacocoTestReport` y análisis SonarQube opcional.

---

## 4. Inventario de endpoints del Backend (REST API)

### Autenticación, Sesión Única y 2FA (`/auth`)

| Servicio / Operación | Método y URL | Auth | Entrada / Parámetros | Respuesta / Códigos HTTP |
|---|---|---|---|---|
| Inicio de sesión (con detección de sesión única y TOTP / Email OTP) | `POST /auth/login` | Pública | JSON `{ correo, password, codigoTotp?, forzarCierreSesionAnterior?, canalMfa? }` (`TOTP` \| `EMAIL`) | `200` (`AuthResponse`: desafío TOTP/Email OTP o tokens), `401`, `403`, `409` (`SESION_ACTIVA_EXISTENTE`), `423`, `429` |
| Verificar desafío MFA (TOTP o OTP recibido por correo) | `POST /auth/totp/verificar` | Pública | JSON `{ challengeToken, codigo, forzarCierreSesionAnterior? }` | `200` (`AuthResponse` + `codigosRecuperacion` en primer enrolamiento TOTP), `401`, `403`, `409`, `423`, `429` |
| Enviar / reenviar código OTP temporal por correo electrónico | `POST /auth/otp/correo/enviar` | Pública | JSON `{ challengeToken }` | `200` (`GenericResponse`), `400`, `401`, `423`, `429` |
| Recuperar acceso 2FA con código de respaldo | `POST /auth/totp/recuperar` | Pública | JSON `{ correo, password, codigoRecuperacion }` | `200` (desafío `ENROLLMENT` nuevo QR/secreto), `401`, `403`, `423`, `429` |
| Solicitar OTP por correo para recuperar 2FA | `POST /auth/totp/recuperar/solicitar-correo` | Pública | JSON `{ correo, password }` | `200` (`challengeToken` de recuperación), `401`, `403`, `423`, `429` |
| Confirmar recuperación 2FA con OTP recibido por correo | `POST /auth/totp/recuperar/confirmar-correo` | Pública | JSON `{ challengeToken, codigo }` | `200` (cierra sesiones previas e inicia `ENROLLMENT` TOTP), `401`, `403`, `423`, `429` |
| Consultar sesión activa del usuario | `GET /auth/sesion-activa` | Bearer | Header `Authorization: Bearer <jwt>` | `200` (`SesionActivaResponse`), `401` |
| Renovar Access Token (rotación refresh) | `POST /auth/refresh` | Pública | JSON `{ refreshToken }` | `200` (`AuthResponse`), `401`, `403`, `429` |
| Cerrar sesión actual | `POST /auth/logout` | Pública | Header `Authorization?`, JSON `{ refreshToken }?` | `200` (`GenericResponse`) |
| Cerrar todas las sesiones activas | `POST /auth/logout-all` | Bearer | Header `Authorization: Bearer <jwt>` | `200`, `401` |
| Auditoría de eventos de login | `GET /auth/auditoria` | `ROLE_ADMIN` | Query: `correo?`, `page` (`0`), `size` (`20`, máx. `100`) | `200` (`PageResponse`), `400`, `401`, `403` |

### Catálogo GestoPago y Proxy de Imágenes (`/gestopago`)

| Servicio / Operación | Método y URL | Auth | Parámetros | Respuesta / Códigos HTTP |
|---|---|---|---|---|
| Consultar catálogo (lista completa, filtrada o paginada) | `GET /gestopago/catalogo` | Pública | Query opcional: `categoria?`, `servicio?`, `q?`, `page?`, `size?` | `200` (`List<CatalogoProducto>` o `PageResponse`), `400`, `404` |
| Listar categorías disponibles | `GET /gestopago/catalogo/categorias` | Pública | — | `200` (`List<CatalogoCategoriaResponse>`) |
| Detalle de producto específico | `GET /gestopago/catalogo/{idServicio}/{idProducto}` | Pública | Path: `idServicio`, `idProducto` | `200` (`CatalogoProducto`), `400`, `404` |
| Logo PNG del servicio (proxy seguro con caché 24h) | `GET /gestopago/servicios/{idServicio}/logo` | Pública | Path: `idServicio` (`^[1-9]\d{0,9}$`) | `200` (`image/png`), `400`, `404`, `429` |
| Imagen guía de referencia | `GET /gestopago/servicios/{idServicio}/referencia` | Pública | Path: `idServicio` | `200` (`image/jpeg`), `400`, `404`, `429` |

### Compras, Conciliación e Historial (`/servicios`)

| Servicio / Operación | Método y URL | Auth | Entrada / Parámetros | Respuesta / Códigos HTTP |
|---|---|---|---|---|
| Realizar compra / recarga / pago de servicio | `POST /servicios/pagos` | `ROLE_CLIENTE` | Header `Idempotency-Key?`, JSON `SendTxRequest` (`numeroCuenta`, `idServicio`, `idProducto`, `telefono`/`referencia`, `montoPago`, `digitoVerificador?`) | `201` (nueva) / `200` (idempotente), `400`, `409`, `422`, `502`, `504` (`upc` + `PENDIENTE_CONCILIACION`) |
| Historial de compras del usuario autenticado | `GET /servicios/historial` | `ROLE_CLIENTE` | Query: `fechaInicio?`, `fechaFin?`, `numeroCuenta?`, `estatus?` (**por defecto `EXITOSA`**), `incluirTodosEstatus` (`false`), `page` (`0`), `size` (`10`), `sortBy`, `sortDir` | `200` (`PageResponse<HistorialCompraResponse>`), `400`, `403`, `404` |
| Detalle de transacción por folio UPC | `GET /servicios/pagos/{upc}` | `CLIENTE` / `ADMIN` | Path: `upc` | `200` (`PagoServicioResponse`), `403`, `404` |
| Conciliar operación pendiente | `POST /servicios/pagos/{upc}/conciliar` | `CLIENTE` / `ADMIN` | Path: `upc` | `200` (`PagoServicioResponse`), `403`, `404`, `502` |

### Onboarding, Clientes, Cuentas y Administración (`/clientes`, `/cuentas`, `/usuarios`, `/catalogos`)
- `POST /clientes` (pública con rate limit por IP): alta de cliente, domicilio, usuario `ROLE_CLIENTE` y cuenta única de ahorro en estatus `PENDIENTE_REVISION`.
- `GET /clientes/mi-perfil`, `GET /clientes/{id}`, `PUT /clientes/{id}` (`CLIENTE` titular o `ADMIN`); `GET /clientes` y `DELETE /clientes/{id}` (`ROLE_ADMIN`).
- `GET /cuentas/mi-cuenta`, `GET /cuentas/mi-cuenta/saldo` (`ROLE_CLIENTE`); `GET /cuentas/pendientes`, `PUT /cuentas/{numeroCuenta}/revision` (`ROLE_ADMIN`).
- `POST /usuarios`, `POST /usuarios/buscar`, `PUT /usuarios/actualizar`, `PUT /usuarios/estado`, **`POST /usuarios/{id}/reset-totp`** (`ROLE_ADMIN`).
- `GET /catalogos/**` pública; escritura `ROLE_ADMIN`.

### Notificaciones automáticas por correo electrónico (SMTP)
- **2FA y recuperación**: envío síncrono de OTP de 6 dígitos (`POST /auth/otp/correo/enviar`, `canalMfa="EMAIL"`, `POST /auth/totp/recuperar/solicitar-correo`) y copia asíncrona de los 8 códigos de respaldo tras el primer enrolamiento TOTP.
- **Alertas de seguridad**: notificación asíncrona ante inicio de sesión exitoso, cierre forzado de sesión previa, recuperación 2FA o reset administrativo TOTP (`POST /usuarios/{id}/reset-totp`).
- **Comprobante de compra GestoPago**: envío asíncrono con folio UPC, servicio, monto, cuenta enmascarada, autorización y saldo disponible al confirmarse `EXITOSA` (directa o por conciliación).
- **Onboarding y dictamen**: confirmación al registrar cliente (`PENDIENTE_REVISION`) y notificación cuando el administrador cambia el estatus de la cuenta (`ACTIVA`, `RECHAZADA`, etc.).

---

## 5. Integración real con GestoPago y limitaciones documentadas

| Servicio externo GestoPago | URL / Path | Método | Headers y Payload | Manejo / Autenticación |
|---|---|---|---|---|
| **Authenticate** (`GestoPagoAuthClient`) | `${GESTOPAGO_URL}/authenticate` | `POST` | Query/Form: `idDistribuidor`, `codigoDispositivo`, `password` | Token almacenado cifrado en `gestopago_tokens` con renovación automática y ante HTTP `401` |
| **CatProduct** (`GestoPagoCatProduct`) | `${GESTOPAGO_URL}/sistema/service/catProduct.do` | `POST` | `Authorization: Bearer <token>`, `X-API-Key`, XML `idDistribuidor`/`codigoDispositivo` | Respuesta XML parseada con defensa XXE (`SUPPORT_DTD=false`) y persistida en MongoDB |
| **SendTx** (`GestoPagoSendTx`) | `${GESTOPAGO_URL}/sistema/service/sendTx.do` | `POST` | `Authorization: Bearer`, `X-API-Key`, form-urlencoded `signed` + `random` (JSON cifrado `AES/CBC/PKCS5Padding`) | Respuesta XML o Base64 cifrado; `SocketTimeoutException` pasa a `PENDIENTE_CONCILIACION` (nunca doble cargo) |
| **VerifyTx** (`AcamGestoPagoFeignConfig`) | `${GESTOPAGO_URL}${gestopago.verify.path}` | `POST` | `Authorization: Bearer`, `X-API-Key`, form-urlencoded `signed` + `random` | Reconciliación manual (`POST /servicios/pagos/{upc}/conciliar`) y job automático `@Scheduled` |
| **Imágenes / Referencias** (`GestoPagoImagenServiceImpl`) | `/sistema/images/gestopago/servicios/{id}.png` y `referencias/{id}.jpg` | `GET` | `Authorization: Bearer`, `X-API-Key` | Streaming acotado (5 MB), sin redirecciones y caché TTL 24h en memoria |

### Limitaciones técnicas del proveedor externo y solución implementada
- **Sin paginación/búsqueda ni categorías estructuradas en `catProduct.do`**: GestoPago entrega el catálogo completo en un solo XML diario. El backend lo sincroniza en MongoDB e implementa categorías (`idCatTipoServicio`), búsqueda, detalle y paginación sin sobrecargar la API externa.
- **Sin gestión nativa de sesiones de usuario final en GestoPago**: GestoPago solo autentica al distribuidor/comercio; la autenticación de usuarios, JWT, 2FA TOTP, códigos de recuperación y exclusividad de sesión única están centralizados e implementados en el backend bancario.
