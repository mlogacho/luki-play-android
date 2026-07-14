# Plan de Migración a Nativo — App Móvil Luki Play (por sprints)

**Fecha:** 2026-07-10 · **Documento vivo** · Complementa a [`ROADMAP_MIGRACION.md`](ROADMAP_MIGRACION.md) (visión por fases) — este plan baja esa visión a **sprints ejecutables para la app móvil**, con criterio de seguridad de aplicaciones y una restricción no negociable aprendida del incidente de julio 2026:

> **La app debe abrir SIEMPRE, en todos los dispositivos de la matriz, en cada release.**
> Ninguna mejora justifica repetir un "pantalla negra" en producción.

---

## 1. Contexto y estado real de partida

La app hoy es un *shell* WebView (Splash → Router → MobileMainActivity) que carga el portal `lukiplay.com`. La migración **no parte de cero**: el repo ya contiene el andamiaje nativo de las fases F1–F5 del roadmap, apagado tras feature flags.

| Ya existe (verificado en código) | Falta (bloqueante para encender lo nativo) |
|---|---|
| Capa de datos: Retrofit/OkHttp + `AuthRepository`, `TokenAuthenticator` (refresh), `SecureTokenStore` cifrado | **Pantalla de login nativa** — `NavGraph.kt` arranca en `PICKER` asumiendo sesión existente |
| Pantallas Compose: Home, Search, Detail, Profiles, Parental PIN, Downloads | Backend: `GET /auth/profiles` responde **404** (por eso `NATIVE_HOME_ENABLED=false`) |
| Player Media3 + Widevine provider + QoS listener | Validación de streams reales contra el player nativo |
| Room (caché catálogo), Hilt, WorkManager, Cast | QA en la matriz de dispositivos; rollout gradual del flujo nativo |
| Crashlytics activo (proyecto `lukiplay-cfb07`, desde 1.0.12) | Baseline de métricas con datos reales (recién empieza a llegar) |
| Blindaje de apertura 1.0.12: fix splash Android ≤ 11, `WebViewSupport` (mín. Chromium 80), `BlankPageWatchdog` | — |

**Implicación:** los sprints son de **completar, encender, endurecer y desplegar**, no de reescribir.

---

## 2. Matriz de apertura garantizada

Dispositivos analizados durante el incidente 2026-07 + pisos de compatibilidad. Todo release (nativo o no) debe pasar la **prueba de apertura** en esta matriz antes de subir a Play.

| Dispositivo / emulador | Android | Motor web | Riesgo específico | Cómo se cubre |
|---|---|---|---|---|
| Emulador API 23 | 6.0 (minSdk) | WebView de fábrica muy viejo | JS moderno no parsea; **CAs raíz viejas** (ver §4.2) | Nativo elimina dependencia del WebView; TLS validado en API 23 |
| Emulador API 30 (`Android11_Test`) | 11 | WebView 83 | Aquí crasheó la 1.0.10 (splash) | Smoke de arranque obligatorio en CI |
| Redmi 8 (real, USB) | 9/10 MIUI | WebView 149 | Restricciones MIUI de instalación | Prueba manual por release |
| Head unit YT9218 (auto) | "10.1" AOSP | WebView congelado, sin updates de Play | **El caso que solo lo nativo resuelve**: su WebView jamás se actualizará | Flujo nativo no toca WebView |
| TV Riviera / boxes AOSP | ≤ 11 | Variable | Ídem head unit + D-pad | Comparte capa de datos; UI TV fuera de alcance de este plan |
| Emulador API 35/36 | 15/16 | Actual | Edge-to-edge, cambios de plataforma | Smoke de arranque en CI |

**Regla de arranque blindado** (lección del vector vacío `ic_splash_empty.xml`):
1. El camino Splash → primer frame no depende de recursos "decorativos" frágiles ni de red.
2. Toda inflación de UI en Activities de entrada va en `try/catch` con pantalla de respaldo accionable (`WebViewSupport.showFallbackScreen` como patrón).
3. Mientras conviva el WebView, `BlankPageWatchdog` sigue armado; cuando el flujo sea nativo, el equivalente es *timeout + estado de error con reintento* en cada pantalla de entrada.
4. **Rollout gradual siempre** (10% → 50% → 100%) con freno automático si crash-free < 99.3%.

---

## 3. Cadencia, equipo y duración

- **Sprints de 2 semanas** (Sprint 0 dura 1 semana).
- Equipo real: **1 dev (Sofia) asistida por IA** + soporte backend puntual (externo al repo). Los estimados asumen dedicación mayoritaria; con dedicación parcial, estirar el calendario, no el alcance por sprint.
- **Duración total: ~12–13 semanas** (Sprint 0 + 6 sprints). El Sprint 6 (DRM/offline) es condicional al negocio.
- TV nativa **queda fuera de este plan** (plan propio después); todo lo que se construya en `data/` y `player/` debe seguir compilando para el target TV.

```
S0 (1s)      S1 (2s)      S2 (2s)      S3 (2s)      S4 (2s)      S5 (2s)      S6 (2s, opc.)
Estabilizar  Login        Perfiles +   Player       Search +     Hardening +  Widevine +
+ CI +       nativo +     Home nativo  nativo HLS   Detail +     rollout      descargas
baseline     sesión       (interno)                 deep links   producción   offline
```

**Dependencia crítica de calendario:** `GET /auth/profiles` del backend bloquea el Sprint 2. **Se solicita formalmente en el Sprint 0** (no en el 2) para dar margen. Si no llega a tiempo, el Sprint 2 implementa *modo perfil único implícito* (fallback documentado en §5, Sprint 2).

---

## 4. Criterios de seguridad transversales (aplican a todos los sprints)

Marco de referencia: **OWASP MASVS** nivel L1 + puntos L2 donde el costo lo justifica. Estado actual y política:

### 4.1 Almacenamiento y credenciales
- `SecureTokenStore` (EncryptedSharedPreferences/Keystore) es la **única fuente de verdad** de la sesión. Ya migrado desde el bridge JS (commit 8625911). Prohibido: tokens en prefs planas, en logs, en URLs, en Crashlytics (ni como breadcrumb — `CrashlyticsTree` solo registra WARN+ sin payloads).
- PIN parental: derivado con Keystore, intentos limitados (ya contemplado en `ParentalControl`).

### 4.2 Red
- Release: `cleartextTrafficPermitted=false`; excepciones solo en debug.
- **Verificar cadena TLS del backend en API 23** (Sprint 1): Android < 7.1.1 no confía en ISRG Root X1 (Let's Encrypt). Si `lukiplay.com` usa Let's Encrypt, la capa nativa fallará en Android 6 aunque el diseño sea perfecto. Mitigación: cadena alternativa en el servidor o *trust anchor* adicional vía `network_security_config`.
- **Certificate pinning: NO por ahora.** Sin proceso maduro de rotación de certificados, el pinning es un arma apuntando al propio pie (una renovación mal hecha = app muerta en campo, y este producto corre en dispositivos que no se actualizan). Decisión revisable cuando exista rotación controlada + kill-switch remoto (Remote Config). Documentar la decisión, no improvisarla.

### 4.3 Superficie del bridge JS (mientras conviva el WebView)
- `@JavascriptInterface` solo expuesto a orígenes de `lukiplay.com` (allowlist de `LukiWebViewClient` se mantiene).
- Cada sprint que mueve una capacidad a nativo **retira** el método equivalente del bridge (superficie decreciente, nunca creciente).
- Ningún dato del bridge se usa sin validar (el `StreamConfig` que llega de JS se valida antes de tocar el player).

### 4.4 Ofuscación y binario
- **Deuda actual:** `proguard-rules.pro` tiene `-keep class com.luki.play.** { *; }` — eso anula la ofuscación de TODO el código propio. Se estrecha en Sprint 5 (keep quirúrgico: bridge, modelos Moshi, entry points) con verificación vía `mapping.txt`.
- `debugSymbolLevel=FULL` ya activo para símbolos nativos en Play.

### 4.5 Secretos y firma
- Keystore de release **fuera de git** (verificado: ignorado en `.gitignore`, no está en el índice). Passwords en `local.properties` no versionado. ✔
- Sprint 0: confirmar inscripción en **Play App Signing** y guardar copia cifrada del upload key fuera de la máquina de desarrollo (si se pierde el keystore sin Play App Signing, se pierde la app).
- `google-services.json` versionado es aceptable (no es secreto; la API key de Firebase se restringe por SHA-1/paquete en la consola — tarea Sprint 0).

### 4.6 Cadena de suministro
- Versiones fijadas en `libs.versions.toml` (sin rangos dinámicos). ✔
- Sprint 0: activar Dependabot/Renovate sobre el catálogo + revisión mensual de avisos de seguridad de AndroidX/Firebase/Media3.

### 4.7 Telemetría con higiene
- Crashlytics: solo diagnóstico técnico. Nada de PII (email, tokens, IPs de usuario) en keys, logs ni non-fatals.

---

## 5. Sprints

### Sprint 0 — Estabilización, baseline y CI de apertura (1 semana)

**Objetivo:** consolidar la 1.0.12 en producción y montar la red de seguridad que protege todos los sprints siguientes. No se escribe UI nueva.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 0.1 | Monitorear rollout 1.0.12: Crashlytics + Android Vitals; configurar alertas de velocidad de crashes | Crash-free users ≥ 99.5% a 7 días; alerta por email activa |
| 0.2 | Sanear pista **Alpha** (aún sirve la 13/1.0.10 rota): actualizarla a 1.0.12 o pausarla | Ninguna pista de Play entrega la versión con el crash |
| 0.3 | CI (GitHub Actions): `assembleDebug` + tests + lint + **smoke de apertura** en emuladores API 23, 30 y 35 (arrancar app, screenshot, verificar frame no-negro) | Pipeline verde obligatorio para mergear a `main` |
| 0.4 | **Solicitud formal al backend**: contrato y fecha de `GET /auth/profiles`; confirmar estabilidad de `/auth/login` y `/auth/refresh` (rotación de refresh token) | Contrato acordado por escrito; fecha comprometida |
| 0.5 | Seguridad de firma: verificar Play App Signing; respaldo cifrado del upload key fuera del equipo de desarrollo | Checklist §4.5 completo |
| 0.6 | Restringir API key de Firebase por paquete + SHA-1 en consola | Restricción visible en Google Cloud Console |
| 0.7 | Activar Dependabot/Renovate sobre `libs.versions.toml` | Primer PR automático recibido |

**Gate de salida:** 1.0.12 estable en 100% de producción + CI con smoke de apertura en verde.

---

### Sprint 1 — Login nativo y dueño único de la sesión (2 semanas)

**Objetivo:** cerrar el hueco más crítico de la migración: hoy ninguna pantalla nativa puede autenticar. Al final del sprint, un build interno hace login sin tocar el WebView.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 1.1 | `LoginScreen` Compose + ruta `LOGIN` en `NavGraph` (startDestination cuando no hay sesión) | Login usuario/contraseña funcional contra `/auth/login` |
| 1.2 | Cablear `AuthRepository` + `TokenAuthenticator`: refresh automático en 401, reintento único, logout en refresh inválido | Test instrumentado: sesión expira → se renueva sin intervención |
| 1.3 | Manejo de errores de login: credenciales inválidas, rate-limit (429), sin red — mensajes accionables, sin filtrar detalle del backend | Estados de error verificados manualmente |
| 1.4 | Sincronización de sesión nativa → WebView (cookie one-way) para que el portal siga funcionando en pantallas aún no migradas | Login nativo + abrir WebView = sesión activa en el portal |
| 1.5 | **TLS en API 23** (§4.2): probar handshake real contra `lukiplay.com` en emulador API 23; aplicar mitigación si falla | Llamada de login exitosa en Android 6 |
| 1.6 | Auditoría de logs: ni Timber ni Crashlytics registran credenciales/tokens (revisar interceptores OkHttp: `HttpLoggingInterceptor` solo en debug y con `redactHeader("Authorization")`) | Grep de logcat en release: cero secretos |

**Seguridad del sprint:** tokens solo en `SecureTokenStore`; contraseña nunca persiste; campo de contraseña con `imeOptions` correctos y sin autofill hacia terceros.

**Gate de salida:** flujo login → sesión → refresh → logout completo en build interno, verificado en API 23, 30 y 35 + Redmi 8.

---

### Sprint 2 — Perfiles + Home nativo encendido en pista interna (2 semanas)

**Objetivo:** encender `NATIVE_HOME_ENABLED` en interno. El usuario interno vive en la app nativa: login → perfil → home.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 2.1 | Integrar `ProfilesRepository` contra `GET /auth/profiles` real | Picker muestra perfiles del backend |
| 2.2 | **Plan B si el backend no llegó** (decisión ya tomada, no improvisar): modo *perfil único implícito* — `ProfilesRepository` sintetiza un perfil default y el picker se salta; se activa por flag | La app nativa no queda bloqueada por el 404 |
| 2.3 | Home nativo con datos reales: filas por categoría, Room como caché (stale-while-revalidate), estados de carga/vacío/error con reintento | TTI Home < 1.5s en gama media; scroll fluido |
| 2.4 | **Fallback de apertura**: si el arranque nativo falla (excepción en Home, API caída), la app cae automáticamente al flujo WebView actual en vez de morir | Simular fallo de API → app abre igual (vía WebView) |
| 2.5 | `NATIVE_HOME_ENABLED=true` en debug + pista interna de Play | Build interno navegable 100% nativo hasta Home |
| 2.6 | Telemetría comparativa: evento de arranque con `flujo=nativo|webview` en Crashlytics keys (sin PII) | Dashboard distingue crashes por flujo |

**Seguridad del sprint:** respuestas del catálogo tratadas como no confiables (parsing tolerante con Moshi, sin `!!`); imágenes solo HTTPS.

**Gate de salida:** pista interna con nativo ON, crash-free 100% en la matriz de apertura durante 1 semana de dogfooding.

---

### Sprint 3 — Player nativo (HLS, sin DRM todavía) (2 semanas)

**Objetivo:** reproducir los canales con Media3/ExoPlayer desde la app nativa. DRM se pospone al Sprint 6: primero estabilidad de reproducción en claro/HLS actual.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 3.1 | `PlayerActivity` conectado al detalle nativo: play desde Home/Detail sin WebView | Canal en vivo reproduce en build interno |
| 3.2 | Robustez de reproducción: manejo de `PlaybackException` con reintento/backoff, recuperación al recuperar red, `KEEP_SCREEN_ON`, audio focus, PiP | Sesión de 30 min sin intervención; rebuffer < 1% |
| 3.3 | `QoSAnalyticsListener` emitiendo: startup time, rebuffers, cambios de bitrate, errores fatales → Crashlytics keys/non-fatals | Métricas visibles en panel |
| 3.4 | Validación en gama baja: emulador API 23/26 con RAM limitada + Redmi 8 | Sin OOM ni ANR en Vitals internos |
| 3.5 | Validar `StreamConfig` (URLs solo de hosts permitidos, esquema https) antes de entregarlo al player — aplica al que venga del bridge mientras conviva | Test unitario de la validación |

**Seguridad del sprint:** URLs de stream firmadas/expirables se piden on-demand y no se cachean en Room; headers de licencia (futuro DRM) jamás en logs.

**Gate de salida:** ver TV en la app nativa de punta a punta en la matriz; QoS con baseline registrado.

---

### Sprint 4 — Search, Detail, deep links y retirada del WebView del flujo principal (2 semanas)

**Objetivo:** completar la paridad del flujo principal móvil. El WebView queda solo como fallback de apertura y para contenido editorial.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 4.1 | `SearchScreen` con datos reales (debounce, resultados por tipo) | Búsqueda funcional contra API |
| 4.2 | `ChannelDetailScreen` completa + gating parental real (`requiereControlParental`) | PIN bloquea reproducción; sin bypass por back-stack |
| 4.3 | Deep links / App Links (`lukiplay.com/canal/...` → Detail nativo) con verificación de dominio | `assetlinks.json` publicado; link abre la app |
| 4.4 | Accesibilidad básica: TalkBack, contraste, touch targets ≥ 48dp | Pasada con Accessibility Scanner sin críticos |
| 4.5 | Tests instrumentados del flujo completo: login → perfil → home → detail → player | Suite verde en CI |
| 4.6 | **Poda del bridge JS**: retirar de `LukiBridge` los métodos que el flujo nativo ya no necesita; documentar los que quedan y por qué | Superficie del bridge reducida y justificada |

**Gate de salida:** un usuario interno hace TODO su uso diario sin que se instancie un WebView.

---

### Sprint 5 — Endurecimiento y rollout a producción (2 semanas)

**Objetivo:** pasar de "funciona en interno" a "producción al 100%" con revisión de seguridad formal.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 5.1 | **Estrechar ProGuard/R8** (§4.4): eliminar el keep global, keeps quirúrgicos, verificar con `mapping.txt` que el código propio ofusca | APK release ofuscado; app funciona idéntica |
| 5.2 | Pentest interno con checklist MASVS-L1: almacenamiento, red, plataforma, código; registrar hallazgos y cierre | 0 hallazgos críticos abiertos |
| 5.3 | Revisión de permisos del manifest (mínimos), `exported=` explícitos, `allowBackup` decidido conscientemente | Manifest auditado |
| 5.4 | Bump versión + release notes; **rollout gradual 10% → 50% → 100%** con criterio de freno: crash-free < 99.3% o regresión de Vitals detiene la ola | 100% alcanzado sin activar el freno |
| 5.5 | Verificación de apertura post-release en la matriz (incluye Redmi real); confirmar en Crashlytics que dispositivos Android ≤ 11 abren el flujo nativo | Cero crashes de arranque en la matriz |
| 5.6 | Actualizar documentación: README, este plan, `ROADMAP_MIGRACION.md` (F3 cerrada) | Docs reflejan estado real |

**Gate de salida:** app nativa móvil en producción al 100%, con el WebView degradado a fallback + editorial.

---

### Sprint 6 — Widevine + descargas offline (2 semanas, **condicional**)

Solo si el negocio confirma contenido premium con DRM. Depende de bloqueantes externos: empaquetado CMAF/DASH y servidor de licencias Widevine (contratos incluidos). Si no están, este sprint se convierte en *buffer* de deuda técnica y pulido.

| # | Tarea | Criterio de aceptación |
|---|---|---|
| 6.1 | Activar `WidevineProvider` contra servidor de licencias real; política L1/L3 por dispositivo (720p máx. en L3) | Reproducción DRM en dispositivo L1 y L3 |
| 6.2 | Descargas offline (`DownloadManager` + `OfflineLicenseAcquirer`) tras `OFFLINE_DOWNLOADS_ENABLED` | Descargar y reproducir en modo avión |
| 6.3 | Seguridad DRM: licencias offline con expiración verificada; claves jamás en logs; storage de descargas en directorio interno | Auditoría de la superficie nueva |

---

## 6. Definition of Done — cada sprint

- [ ] **Prueba de apertura en la matriz completa** (§2): emuladores API 23/30/35 + Redmi 8. La app abre y llega a UI interactiva.
- [ ] CI verde: build + tests + lint + smoke de apertura.
- [ ] Cero secretos nuevos en repo, logs o telemetría (revisión rápida por sprint).
- [ ] Feature flag para cada capacidad nueva → rollback sin release.
- [ ] Crashlytics sin regresión de crash-free respecto al sprint anterior.
- [ ] Lo aprendido/decidido queda en el doc correspondiente (este plan es un documento vivo).

---

## 7. Riesgos principales

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| `GET /auth/profiles` no llega a tiempo | Alta | Bloquea Sprint 2 | Pedido formal en Sprint 0 + Plan B perfil único implícito (tarea 2.2) |
| API del backend cambia sin aviso (hoy la consume solo la web) | Media | Roturas silenciosas en nativo | Parsing tolerante, contrato por escrito (0.4), tests contra MockWebServer |
| TLS falla en Android 6 (CAs viejas) | Media | Nativo no funciona justo en los dispositivos viejos que motivan la migración | Tarea 1.5 al inicio; mitigación en servidor o `network_security_config` |
| Un solo dev: cuello de botella y bus factor | Alta | Calendario | Sprints con gates estrictos (no arrastrar deuda); docs vivos; CI como segunda memoria |
| Dispositivos AOSP raros (head units) con comportamientos no estándar | Media | Crashes exóticos | Crashlytics ya activo = visibilidad real; rollout gradual como amortiguador |
| Tentación de encender DRM/offline antes de estabilizar | Media | Complejidad prematura | Sprint 6 explícitamente condicional |

---

## 8. Anti-objetivos

- **No** tocar la UI de TV en este plan (plan propio post-Sprint 5); solo mantener `data/` y `player/` compatibles.
- **No** reescribir el backend; los gaps se puentean con fallbacks documentados.
- **No** eliminar el flujo WebView hasta que el nativo lleve ≥ 2 releases estables al 100% — es la red de seguridad de apertura.
- **No** activar certificate pinning sin proceso de rotación (decisión §4.2).
- **No** migrar el contenido editorial (FAQ, términos): se queda en WebView por diseño.
