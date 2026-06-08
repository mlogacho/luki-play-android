# Roadmap de Migración — LukiPlay Android → Nativo (híbrido estratégico)

**Documento complementario a:** `AUDITORIA_LUKIPLAY_ANDROID.md`
**Estrategia:** Migración híbrida estratégica — nativo para Player + Sesión + Catálogo + TV; WebView para contenido editorial.
**Stack destino:** Kotlin + Jetpack Compose (móvil) + Compose for TV + Media3/ExoPlayer + Widevine + Hilt + Retrofit/OkHttp.
**Duración total estimada:** 22–26 semanas (~5–6 meses) con equipo de 2 Android sr + 1 QA + soporte backend.

---

## Principios rectores

1. **No big-bang.** Cada fase entrega un APK publicable a producción.
2. **Coexistencia controlada.** Hasta que una pantalla nativa esté lista, el WebView sigue siendo source-of-truth.
3. **Feature flags desde día 1** (Firebase Remote Config o similar) — permite rollout gradual y rollback inmediato.
4. **Tests obligatorios** en código nuevo — todo lo nativo nace con tests; el código WebView legacy se mantiene sin tests hasta que se reemplace.
5. **Telemetría primero.** Antes de migrar una pantalla, instrumentar la actual para tener baseline (TTFB, startup, errores).
6. **Backend en paralelo.** Cada fase tiene dependencia backend explícita — no bloquearse esperando.

---

## Vista general de fases

| Fase | Nombre | Duración | Dependencia backend | Publicable |
|---|---|---|---|---|
| 0 | Hardening de seguridad (inmediato) | 2 semanas | No | Sí — v1.0.2 |
| 1 | Cimientos nativos (DI, HTTP, Auth, almacenamiento seguro) | 3 semanas | Refresh endpoint estable | Sí — v1.1 |
| 2 | Player nativo + Widevine + Chromecast + QoS | 4–5 semanas | **Sí**: empaquetado CMAF + servidor de licencias | Sí — v1.2 |
| 3 | Catálogo / Home / Search móvil en Compose | 4 semanas | API REST estable | Sí — v1.3 |
| 4 | Android TV nativo (Compose for TV) | 5–6 semanas | API consumida ya | Sí — v2.0 |
| 5 | Control parental, multiperfil, descargas offline | 4 semanas | Endpoints de perfiles + licencias offline | Sí — v2.1 |

---

## FASE 0 — Hardening de la app actual (2 semanas) — **PRIORIDAD MÁXIMA**

**Objetivo:** Tapar los hallazgos críticos de seguridad y compliance **sin esperar** la migración. Entrega un parche publicable.

### Alcance

| Tarea | Archivos | Criterio de aceptación |
|---|---|---|
| Migrar tokens a `EncryptedSharedPreferences` | `bridge/LukiBridge.kt`, nuevo `util/SecureStorage.kt` | Tokens cifrados en disco; verificado con `adb pull` |
| Restringir `cleartextTrafficPermitted` a dominios específicos | `res/xml/network_security_config.xml` | base-config en `false`; dev IP solo en `debug` flavor |
| Silenciar logs en release | nuevo `util/Logger.kt` (Timber con tree release) | `Log.*` reemplazados; release no imprime tokens en logcat |
| Privacy Policy URL + Data Safety form en Play | `AndroidManifest.xml` + Play Console | Formulario aprobado |
| Quitar `MIXED_CONTENT_ALWAYS_ALLOW` o limitarlo a debug | `webview/WebViewConfig.kt:65-67` | Solo en debug flavor |
| ABI splits o AAB para reducir tamaño | `app/build.gradle.kts` | AAB en Play Console |

### Criterios de éxito de fase
- Pentest interno: 0 hallazgos críticos de almacenamiento de credenciales.
- APK release no muestra tokens en logcat.
- Privacy Policy publicada y enlazada en Play.
- Tamaño AAB ≤ 25 MB.

### Riesgos
- Cambiar el esquema de prefs puede invalidar sesiones existentes → implementar migración one-shot que lea el plano y reescriba cifrado.

---

## FASE 1 — Cimientos nativos (3 semanas)

**Objetivo:** Plataforma técnica para todo lo que viene. **Invisible al usuario.**

### Alcance

| Tarea | Entregable |
|---|---|
| Multi-módulo Gradle: `:app`, `:core-common`, `:core-data`, `:core-domain` | Estructura Gradle versionada |
| Hilt en toda la app | `@HiltAndroidApp`, módulos por capa |
| Retrofit + OkHttp + Moshi + interceptor de auth (Bearer + refresh) | `core-data/network/` |
| `DataStore` (Proto) para preferencias no sensibles + `EncryptedSharedPreferences` (sensibles) | `core-data/storage/` |
| Repository de Auth nativo: login, refresh automático, logout | `core-domain/auth/` |
| Coroutines + Flow en toda la capa de datos | — |
| Timber con DebugTree (debug) y CrashlyticsTree (release) | — |
| CI: GitHub Actions con `assembleDebug`, `test`, `lint`, `detekt` | `.github/workflows/android.yml` |
| Cobertura mínima 70% en módulos nuevos | JaCoCo report en CI |

### Criterios de éxito
- WebView sigue funcionando idénticamente (no debe haber regresión visible).
- Auth ya pasa por capa nativa (el bridge ahora delega al repository); el WebView solo dispara intents.
- CI verde con tests y lint.

### Dependencias
- Backend: endpoint `/auth/refresh` debe responder estable con `accessToken` y `refreshToken` rotados.

### Riesgos
- Doble fuente de verdad de sesión (WebView cookies vs prefs nativas) durante la transición → definir prefs nativas como SOT, sincronizar cookies vía `CookieManager` cuando cambien.

---

## FASE 2 — Player nativo con Widevine + Chromecast + QoS (4–5 semanas) — **NÚCLEO**

**Objetivo:** Convertir el player en pieza certificable para contenido premium.

### Alcance

| Tarea | Entregable |
|---|---|
| Activar `DefaultDrmSessionManager` con Widevine | `core-player/drm/WidevineProvider.kt` |
| Soporte DASH además de HLS | dependencia `media3-exoplayer-dash` |
| Custom `TrackSelector` con preferencias de usuario (calidad, idioma) | `core-player/tracks/` |
| Adaptive Bitrate tuning + `LoadControl` ajustado a OTT live | configuración explícita |
| QoS analytics (startup time, rebuffer ratio, bitrate switches, fatal errors) → Firebase/Mux/Conviva | `core-player/qos/` |
| Chromecast (Cast Framework + Expanded Controller) | `feature-cast/` |
| PiP en móvil mejorado (controles, retorno) | `feature-player/pip/` |
| Reemplazar inyección de subtítulos hardcoded por selección de pista real | `core-player/subtitles/` |
| MediaSession real para controles del lockscreen y mando TV | `core-player/session/` |
| Tests instrumentados con streams de referencia | `androidTest/` |

### Dependencias backend (críticas)
- **Empaquetado CMAF (fMP4 + HLS/DASH playlists)** del catálogo premium.
- **Servidor de licencias Widevine** (axinom, ezdrm, irdeto, o self-hosted) con endpoint `/license/widevine`.
- Endpoints de stream que devuelvan `licenseUrl` + `licenseHeaders` en el JSON de `StreamConfig`.

### Criterios de éxito
- Reproducción Widevine L1 verificada en dispositivo real (Pixel + Chromecast con Google TV + un Mi Box).
- Startup time p95 < 2.5s en red Wi-Fi.
- Rebuffer ratio < 1% en pruebas de 30 min.
- Cast funcional con paso de sesión y control desde móvil.

### Riesgos
- L1 vs L3: dispositivos sin L1 (la mayoría de TV box baratos) caen a L3 → política clara de "1080p L1 / 720p L3" por estudio.
- Licenciamiento contractual con estudios — bloqueante externo al equipo Android.

---

## FASE 3 — Catálogo / Home / Search móvil en Compose (4 semanas)

**Objetivo:** Quitar el WebView de la experiencia principal en móvil.

### Alcance

| Tarea | Entregable |
|---|---|
| `feature-home`: hero carousel, filas por categoría, sliders | Compose + Coil + Paging 3 |
| `feature-search`: búsqueda con debounce, resultados por tipo | Compose + Flow |
| `feature-channel-detail`: ficha de canal/contenido | Compose |
| Room para cache offline del catálogo (no contenido) | `core-data/db/` |
| Deep linking + Android App Links | `AndroidManifest.xml` |
| Pull-to-refresh, skeleton loaders, estados vacíos | Material 3 |
| A11y básico (TalkBack, contraste, touch targets) | Auditoría con `accessibility-scanner` |
| Compose UI tests para flows principales | `androidTest/` |

### Criterios de éxito
- TTI (time to interactive) Home < 1.5s en gama media.
- Scroll a 60fps en filas con imágenes.
- WebView legacy queda solo para: FAQ, términos, ayuda, promociones editoriales.
- Crash-free users > 99.7% en una semana de release gradual (10%→50%→100%).

### Riesgos
- Paridad de contenido con el portal web → tener API REST consistente; cualquier feature solo-web obliga a backportear al backend o aceptar gap temporal.

---

## FASE 4 — Android TV nativo en Compose for TV (5–6 semanas) — **DIFERENCIADOR**

**Objetivo:** Experiencia TV competitiva. Borrar definitivamente el WebView+JS de D-Pad.

### Alcance

| Tarea | Entregable |
|---|---|
| Módulo `:app-tv` separado, compartiendo `:core-*` con móvil | Gradle |
| Home TV con `TvLazyColumn` + `TvLazyRow` (filas + carruseles) | `androidx.tv:tv-foundation` |
| Componentes Material TV: `Card`, `ImmersiveList`, `Carousel` | `androidx.tv:tv-material` |
| Focus management nativo (sin JS), animaciones de focus, escala | Compose for TV |
| Player TV con controles adaptados a D-Pad y `PlaybackTransportControlGlue` (Leanback) si se requiere | mixto Compose-TV + Leanback puntual |
| Integración con Recommendations Channel y Watch Next (Android TV Home) | `androidx.tvprovider` |
| Search TV con teclado on-screen + voz (`SpeechRecognizer`) | `feature-search-tv` |
| Banner TV actualizado, overscan correcto, 10-foot UI typography | recursos `tv` |
| Pruebas en dispositivos reales: Chromecast con Google TV, Mi Box, Sony Bravia, TCL/Hisense | matriz QA |

### Criterios de éxito
- Pasa **Google Play for TV App Quality** completo (no solo los mínimos).
- D-Pad navega 100% sin trampas JS, sin "perderse" el focus.
- Aparece en row de recomendaciones del launcher de Android TV.
- Tiempo de arranque hasta Home < 3s en Chromecast con Google TV.

### Riesgos
- Compose for TV aún evoluciona — fijar versión estable y monitorear changelogs.
- Dispositivos TV de gama baja con poca RAM → presupuestar performance (limitar imágenes en memoria, paging agresivo).

---

## FASE 5 — Control parental, multiperfil, descargas offline (4 semanas)

**Objetivo:** Features de retención y diferenciación.

### Alcance

| Tarea | Entregable |
|---|---|
| Multiperfil (hasta N perfiles por cuenta) | `feature-profiles` |
| PIN nativo de control parental (almacenado con Keystore/Cipher) | `feature-parental` |
| Gating real por `Channel.requiereControlParental` | en `feature-player` y `feature-home` |
| Descargas offline con Media3 `DownloadManager` + `DownloadService` | `feature-downloads` (solo móvil) |
| Licencias Widevine offline (`OfflineLicenseHelper`) | core-player |
| WorkManager para reintentos y gestión de cuota | — |
| UI de gestión: lista, progreso, eliminar, espacio en disco | Compose |

### Dependencias backend
- Endpoints de perfiles (`GET/POST/PATCH/DELETE /profiles`).
- Licencias Widevine con flag `offline` y duración configurable.
- Tracking de descargas por cuenta (anti-abuso).

### Criterios de éxito
- Descargar + reproducir offline funciona en avión.
- PIN no se puede bypassear desde UI; intentos limitados.
- Cambio de perfil refresca catálogo y recomendaciones.

### Riesgos
- Licencias offline tienen lifecycle complejo (renew, expiración) → telemetría dedicada.

---

## Dependencias críticas y orden de ejecución

```
FASE 0 (Hardening) ──► FASE 1 (Cimientos) ──┬──► FASE 2 (Player + DRM)
                                            │            │
                                            ├──► FASE 3 (Móvil Compose)
                                            │            │
                                            └──► FASE 4 (TV Compose)
                                                         │
                                                         └──► FASE 5 (Parental + Downloads)
```

- **F0 y F1 son secuenciales y bloqueantes** para todo lo demás.
- **F2, F3, F4 pueden paralelizarse parcialmente** si hay equipo (mínimo 2 devs por track).
- **F5 requiere F2 (DRM) y F4 (TV) completos**, aunque las descargas offline son solo móvil.

---

## Métricas de éxito globales (post-migración)

| Métrica | Baseline actual (estimado) | Objetivo post-migración |
|---|---|---|
| Crash-free users (móvil) | desconocido (sin Crashlytics confirmado) | ≥ 99.7% |
| Startup time (cold) Home | ~3–5s (WebView) | ≤ 1.5s |
| Player startup p95 | desconocido | ≤ 2.5s |
| Rebuffer ratio | desconocido | < 1% |
| Cobertura de tests | 0% | ≥ 70% en módulos nuevos |
| Pasa Play for TV Quality completo | parcial | sí, todos los puntos |
| Soporta Widevine L1 | no | sí |
| Tokens cifrados en reposo | no | sí (desde F0) |

---

## Equipo y costos

| Rol | Dedicación | Justificación |
|---|---|---|
| 2 Android Sr (Kotlin + Compose + Media3) | full-time, todas las fases | Tracks paralelos móvil/TV |
| 1 QA con dispositivos TV reales | full-time desde F2 | Sin esto, F4 no certifica |
| 1 Backend (parcial, F2 y F5) | 30% | Empaquetado, licencias, perfiles |
| 1 UX/UI con experiencia 10-foot UI | 50% en F3 y F4 | TV no es móvil agrandado |
| 1 DevOps (CI/CD, signing, distribución) | 20% | Pipeline Gradle + Play deploy |

---

## Checklist de "Definition of Done" por fase

Cada fase no se cierra hasta cumplir:

- [ ] Tests unitarios e instrumentados en módulos nuevos ≥ 70% coverage.
- [ ] CI verde (build + test + lint + detekt).
- [ ] QA manual con script firmado en dispositivos representativos.
- [ ] Release notes + changelog público.
- [ ] Feature flag para rollback inmediato.
- [ ] Telemetría instrumentada y dashboard verificable.
- [ ] Documentación técnica del módulo en `docs/`.
- [ ] Pentest de superficie nueva (mínimo revisión interna).
- [ ] Aprobado por Producto + QA + Arquitectura.

---

## Anti-objetivos (qué NO hacer)

- **No reescribir el backend** como parte de esta migración. Si algo del backend falta, se acepta gap temporal documentado.
- **No migrar el WebView editorial** (FAQ, términos, ayuda). Esas pantallas cambian a menudo y no necesitan nativo.
- **No usar Flutter / React Native** para "acelerar". El argumento de la migración es precisamente recuperar control nativo (DRM, TV, performance). Un híbrido nuevo repetiría el problema.
- **No saltar Fase 0.** Los hallazgos críticos no esperan 6 meses.
- **No esperar al "diseño perfecto"** de la app TV. Iterar con Compose for TV es rápido; bloquearse en Figma es mortal.

---

**Documento vivo.** Actualizar al cierre de cada fase con métricas reales, lecciones aprendidas y ajustes al cronograma.
