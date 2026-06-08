# Auditoría Técnica — LukiPlay Android

**Cliente:** LukiPlay (OTT)
**Auditor:** Arquitectura Senior OTT — perspectiva Netflix / Disney+ / HBO Max
**Fecha:** 2026-05-29
**Alcance:** App Android (móvil + Android TV), módulo `tv-activation`
**Stack revisado:** Kotlin 2.2.10, AGP 9.2.1, minSdk 21 / targetSdk 35, Media3 1.4.1

---

## TL;DR ejecutivo

LukiPlay Android **NO es una app nativa**: es un **shell híbrido WebView + Player nativo (Media3/ExoPlayer)**. La capa de UI, navegación, sesión, catálogo y control parental viven en `https://lukiplay.com/home` cargado en WebView; solo el reproductor de video es nativo. Esta arquitectura es funcional para un MVP pero **compromete seguridad, experiencia TV, certificación DRM premium y mantenibilidad** a mediano plazo.

**Veredicto global:** **Aceptable para MVP / Deficiente para producto OTT comercial / Crítico para contenido premium con Widevine L1.**
**Recomendación:** Migración **híbrida estratégica** — nativizar Player, Sesión, Catálogo y experiencia TV; conservar WebView solo para contenido editorial (FAQ, términos, ayuda). Ver `ROADMAP_MIGRACION.md`.

---

## FASE 1 — Auditoría del estado actual

### 1.1 Arquitectura actual

**Clasificación:** Híbrido WebView + Native Player (no React Native, no Flutter, no Capacitor).

| Capa | Tecnología | Evidencia |
|---|---|---|
| Shell Android | Kotlin + AppCompat + Views (XML) | `app/build.gradle.kts:1-110`, `MainActivity.kt:28-39` |
| UI / Catálogo / Login | WebView cargando `BuildConfig.BASE_URL` | `mobile/MobileMainActivity.kt:38-143`, `tv/TvMainActivity.kt:30-124` |
| Reproductor | Media3 ExoPlayer 1.4.1 | `player/PlayerActivity.kt:96-203`, `player/LukiPlayerManager.kt:48-95` |
| Bridge JS↔Nativo | `@JavascriptInterface window.LukiNative` | `bridge/LukiBridge.kt:31-294` |
| Routing TV vs móvil | `DeviceUtils.isTv()` | `MainActivity.kt:28-39`, `util/DeviceUtils.kt:60-70` |

**No hay Jetpack Compose. No hay Leanback. No hay Hilt/Koin.** Toda la UI es WebView; lo único "nativo" visible al usuario es `PlayerActivity`.

### 1.2 Player de video

- **Motor:** Media3 / ExoPlayer 1.4.1 (`gradle/libs.versions.toml:9`).
- **Protocolos:** Solo **HLS** (`media3-exoplayer-hls`). No DASH, no SmoothStreaming, no RTSP.
- **DRM:** **NO activado.** `StreamConfig.drmToken` existe como nullable pero `LukiPlayerManager` lo ignora (`StreamConfig.kt:27-29`, `LukiPlayerManager.kt:61`). Comentario explícito en el código: *"⚠️ DRM Widevine no activado"*.
- **Subtítulos:** Soportados (WebVTT/SRT/TTML) pero **idioma hardcodeado a `"es"`** y sin UI de selección (`LukiPlayerManager.kt:75-85`, `PlayerActivity.kt:158-175` oculta el botón).
- **UI custom:** Para canales lineales se ocultan progress/position/duration/settings (`PlayerActivity.kt:168-175`) — decisión correcta para live, pero acoplada en código.
- **Persistencia de posición:** `SavedStateHandle` (`PlayerViewModel.kt:120-129`).

### 1.3 Autenticación y sesiones

- **Almacenamiento:** `SharedPreferences` **plano, sin encriptar** (`luki_prefs` — `util/Constants.kt:53`).
- **Tokens persistidos:** `accessToken`, `refreshToken`, `userId`, `displayName` (`bridge/LukiBridge.kt:124-148`).
- **Flujo:** WebView hace login → backend devuelve JWT → JS invoca `window.LukiNative.onLoginSuccess(...)` → bridge persiste en SharedPreferences (`LukiBridge.kt:124-148`).
- **Recuperación:** `getStoredSession()` devuelve JSON al WebView para rehidratar (`LukiBridge.kt:164-174`).
- **Logout:** Borra prefs + cookies + cache + history (`MobileMainActivity.kt:276-282`, `LukiBridge.kt:183-192`).
- **deviceId:** `Settings.Secure.ANDROID_ID` (`LukiBridge.kt:234-236`) — aceptable, pero rotable por usuario, no es identificador estable.

### 1.4 Control parental y perfiles

**No existen.**

- Campo `Channel.requiereControlParental: Boolean` definido en el modelo (`util/LukiApiClient.kt:24`) pero **nunca consumido** en código nativo.
- No hay PIN, no hay edad mínima, no hay multiperfil, no hay parental gate.
- Toda la lógica (si existe) vive en la web. La app nativa **no impone ninguna restricción**.

### 1.5 Calidad del código

| Aspecto | Estado |
|---|---|
| Patrón | MVVM básico solo en Player; resto procedural en Activities |
| DI | Ninguno (constructor manual) |
| Tests | **0 archivos de test** — sin unit, sin instrumentation |
| HTTP client | `HttpURLConnection` manual en `LukiApiClient` — sin retry, sin timeouts configurables, sin interceptors |
| Logging | `Log.d/i` directos, sin Timber, **no se silencian en release** |
| Inyección JS | D-Pad helper y viewport fixes hardcodeados como strings inline (`MobileMainActivity.kt:47-131`, `TvMainActivity.kt:40-108`) |
| Coroutines/Flow | No usadas |
| ProGuard | Habilitado en release con reglas mínimas correctas (`proguard-rules.pro`) |
| LOC | ~2.247 Kotlin — pequeño, manejable |

**Deuda técnica evidente:** ausencia total de tests, HTTP manual, sin DI, JS bridge sin contratos versionados, logs filtrando tokens en debug.

### 1.6 Soporte Android TV

**Existe a nivel de manifest y navegación D-Pad, pero NO hay UI nativa de TV.**

| Requisito Android TV | Estado | Evidencia |
|---|---|---|
| `android.software.leanback` declarado (`required=false`) | ✅ | `AndroidManifest.xml:52` |
| `LEANBACK_LAUNCHER` intent-filter | ✅ | `AndroidManifest.xml:100` |
| Banner TV 320×180 | ✅ | `res/drawable/banner_tv.png`, `AndroidManifest.xml:71` |
| Touchscreen `required=false` | ✅ | `AndroidManifest.xml:47` |
| Orientación landscape forzada en TV | ✅ | `AndroidManifest.xml:124` |
| Librería Leanback (`androidx.leanback`) | ❌ | No importada |
| Compose for TV (`androidx.tv.material3`) | ❌ | No importada |
| `BrowseFragment` / `DetailsFragment` / Rows nativos | ❌ | No existen |
| Focus management D-Pad | ⚠️ | Hecho con **JS inyectado** (`TvMainActivity.kt:57-108`) — frágil |

**El "Android TV" actual es un WebView en landscape con un script JS que mapea ArrowKeys → focus()/click().** Esto funciona en demos y certifica el TV App Quality básico, pero produce una experiencia muy inferior a la esperada en TV (sin animación de focus, sin overscan handling real, sin row recycling, sin paginación tipo Leanback).

---

## FASE 2 — Diagnóstico crítico

| Dimensión | Veredicto | Justificación |
|---|---|---|
| **Rendimiento del Player** | **Deficiente** | Media3 está bien, pero sin Widevine (L1/L3) la app **no puede reproducir contenido premium** de estudios. HLS-only excluye catálogos DASH/CMAF comunes en OTT. Sin ABR tuning, sin métricas QoS (bitrate, rebuffer, startup time). |
| **Experiencia Android TV** | **Crítico** | UI es WebView con JS de D-Pad. No usa Leanback ni Compose for TV. Sin focus visual nativo, sin patrones de filas/carruseles del ecosistema TV. Imposible competir con Netflix/Disney+/Prime en sensación de "TV app real". Riesgo alto en revisión de Google Play for TV. |
| **Seguridad** | **Crítico** | Tokens JWT en `SharedPreferences` plano (`LukiBridge.kt:124-148`). `network_security_config.xml:15` permite **cleartext globalmente en base-config**. Sin certificate pinning. Logs de tokens visibles en logcat de release si alguien activa `Log` (no hay tree de Timber que filtre). `mixedContentMode = ALWAYS_ALLOW` (`WebViewConfig.kt:65-67`). |
| **Escalabilidad** | **Deficiente** | Chromecast ❌, PiP solo móvil ✅, descargas offline ❌, DRM ❌, multi-audio ❌, analytics QoS ❌. Cada feature OTT estándar requiere reescribir capas porque hoy dependen del WebView. |
| **Mantenibilidad** | **Deficiente** | 0 tests, sin DI, HTTP manual, JS inline en Activities, dos `MainActivity` (mobile/tv) duplicando configuración de WebView. Onboarding de un dev nuevo: alto coste por la mezcla WebView/Native y la falta de contratos del bridge. |
| **Store compliance** | **Aceptable con riesgos** | targetSdk 35 ✅, permisos mínimos ✅, signing config OK ✅. **Pero**: sin Privacy Policy URL en manifest, sin Data Safety completo, tokens sin encriptar (puede ser observado en auditoría de Play), Play for TV puede rechazar por experiencia D-Pad subóptima. |

---

## FASE 3 — Recomendación de migración

### ¿Migrar a nativo?

**Sí.** La arquitectura actual es un techo de cristal para LukiPlay como producto OTT comercial. La justificación no es estética sino concreta:

1. **DRM Widevine L1** requiere `MediaDrm` nativo y `DefaultDrmSessionManager` en ExoPlayer. Mientras la sesión y el catálogo vivan en WebView, **no hay forma de licenciar contenido premium** (estudios exigen L1 demostrable).
2. **Experiencia Android TV competitiva** no se logra con WebView + JS de D-Pad. Google revisa Play for TV con esa vara.
3. **Seguridad de sesión**: JWT en SharedPreferences plano es un hallazgo de pentest garantizado y un riesgo regulatorio (LATAM/UE).
4. **Chromecast, PiP, offline downloads, analytics QoS, watchlist sincronizada** — todas estas features son costosas o imposibles en el modelo actual.

### Tipo de migración: **Híbrida estratégica** (no big-bang)

Migrar **completamente a nativo** lo crítico, **conservar WebView** lo accesorio:

| Capa | Destino |
|---|---|
| Player + DRM + QoS analytics | **Nativo** (Media3 + Widevine) |
| Sesión, login, refresh, almacenamiento seguro | **Nativo** (EncryptedSharedPreferences + DataStore) |
| Catálogo, Home, Search, Detalles | **Nativo** (Compose móvil + Compose for TV) |
| Control parental + perfiles | **Nativo** |
| Settings, FAQ, Términos, Ayuda, Promociones editoriales | **WebView** (cambia rápido, no necesita performance) |

### Stack recomendado

**Móvil:**
- Kotlin 2.x + Coroutines + Flow
- Jetpack Compose + Material 3
- Hilt (DI)
- Media3 / ExoPlayer + Widevine DRM
- Retrofit + OkHttp + Moshi/kotlinx-serialization
- Coil (imágenes)
- EncryptedSharedPreferences + DataStore Proto
- Room (catálogo cacheado + descargas offline)
- WorkManager (descargas)
- Cast Framework (Chromecast)
- Timber (logging con tree release que filtra PII)

**Android TV:**
- **Compose for TV** (`androidx.tv:tv-foundation`, `androidx.tv:tv-material`) — moderno, preferido sobre Leanback Fragments
- Leanback solo para piezas que Compose for TV aún no cubre bien (ej. `PlaybackTransportControlGlue` si conviene)
- Mismo Media3 / Widevine compartido con móvil
- Módulo `:tv` separado para UI; lógica de dominio en módulo `:core` compartido

**Arquitectura:**
- Multi-módulo: `:app-mobile`, `:app-tv`, `:core-domain`, `:core-data`, `:core-player`, `:core-ui`, `:feature-home`, `:feature-player`, `:feature-auth`
- MVI o MVVM + UDF (unidirectional)
- Tests: JUnit5 + MockK + Turbine + Compose UI Test + Espresso

### Esfuerzo estimado y riesgos

| Hito | Duración estimada (equipo de 2 Android sr + 1 QA) |
|---|---|
| Fase 0 — Hardening de la app actual (tokens, cleartext, logs, Privacy Policy) | **2 semanas** |
| Fase 1 — Nativizar Auth + Sesión + cliente HTTP + DI | **3 semanas** |
| Fase 2 — Nativizar Player con Widevine + QoS + Chromecast | **4–5 semanas** |
| Fase 3 — Home/Catálogo móvil en Compose | **4 semanas** |
| Fase 4 — Android TV en Compose for TV | **5–6 semanas** |
| Fase 5 — Control parental + perfiles + descargas offline | **4 semanas** |
| **Total realista** | **22–26 semanas (~5–6 meses)** |

**Riesgos principales:**
- **Licenciamiento Widevine L1**: dependencia de proveedor de licencias (axinom, ezdrm, irdeto). Bloqueante para contenido premium.
- **Backend no preparado**: si el backend hoy solo entrega HLS sin encripción, hay que añadir empaquetado CMAF + servidor de licencias.
- **Paridad con web**: cada feature que hoy "ya está" en el portal web debe replicarse nativo. Tentación de "dejar esta pantalla en WebView" se vuelve permanente.
- **Equipo**: si el equipo actual es mayoritariamente web, contratar/formar Android sr es ruta crítica.
- **Android TV QA**: requiere dispositivos físicos (Chromecast con Google TV, Mi Box, TVs Sony/TCL) — no basta emulador.

### Orden de migración (qué primero y por qué)

1. **Player + Widevine** — desbloquea contenido premium, es el corazón del producto, y es donde el WebView ya **no** está (mínimo conflicto).
2. **Auth + Sesión segura** — quita el hallazgo de seguridad y prepara el resto.
3. **Home/Catálogo móvil nativo** — el WebView del home es la peor experiencia percibida en móviles modernos.
4. **Android TV nativo (Compose for TV)** — el WebView en TV es lo más débil hoy; nativizar abre certificación Play for TV de verdad.
5. **Control parental + perfiles + descargas** — features de retención, después de tener base sólida.

---

## FASE 4 — Hallazgos con cita directa

### 🔴 Críticos

| ID | Hallazgo | Archivo:Línea |
|---|---|---|
| SEC-001 | JWT access/refresh en SharedPreferences sin encriptar | `bridge/LukiBridge.kt:124-148`, `util/Constants.kt:53` |
| SEC-002 | `base-config cleartextTrafficPermitted="true"` (global) | `res/xml/network_security_config.xml:15` |
| SEC-003 | `mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW` en WebView | `webview/WebViewConfig.kt:65-67` |
| PLAYER-001 | Widevine DRM no activado — bloquea contenido premium | `player/StreamConfig.kt:27-29`, `player/LukiPlayerManager.kt:61` |
| TV-001 | "Android TV" es WebView + JS de D-Pad, sin Leanback/Compose-TV | `tv/TvMainActivity.kt:57-108` |
| QA-001 | 0 tests en todo el proyecto | `app/src/test/`, `app/src/androidTest/` inexistentes |
| ARQ-001 | Sin DI framework — código no testeable | `bridge/LukiBridge.kt:31-35` |

### 🟡 Altos

| ID | Hallazgo | Archivo:Línea |
|---|---|---|
| HTTP-001 | `HttpURLConnection` manual, sin retry/timeouts/interceptors | `util/LukiApiClient.kt` (todo el archivo) |
| LOG-001 | Logs `Log.d/i` con datos de sesión, sin gating por BuildConfig | múltiples (`LukiBridge.kt`, `PlayerActivity.kt`) |
| OTT-001 | Sin Chromecast / RemotePlayback | no implementado |
| OTT-002 | Sin descargas offline | no implementado |
| PARENTAL-001 | `Channel.requiereControlParental` definido pero ignorado | `util/LukiApiClient.kt:24` |
| NAV-001 | JS de focus inyectado inline en TvMainActivity (frágil) | `tv/TvMainActivity.kt:40-108` |
| STORE-001 | Sin Privacy Policy URL declarada para Play Store | `AndroidManifest.xml` |

### 🟢 Medios / bajos

| ID | Hallazgo | Archivo:Línea |
|---|---|---|
| ARQ-002 | Dos MainActivity duplican configuración WebView | `mobile/MobileMainActivity.kt`, `tv/TvMainActivity.kt` |
| CODE-001 | Subtítulos hardcoded a `"es"` | `player/LukiPlayerManager.kt:80` |
| BUILD-001 | ABI splits deshabilitados — APK pesado | `app/build.gradle.kts:79` |
| ICON-001 | Adaptive icons posiblemente incompletos en densidades | `res/mipmap-*` |

---

## Tabla de diagnóstico final

| Dimensión | Veredicto | Acción mínima | Acción ideal |
|---|---|---|---|
| Player | Deficiente | Activar Widevine L3 | Widevine L1 + CMAF + QoS analytics |
| TV UX | **Crítico** | (no hay parche, requiere reescritura) | Compose for TV nativo |
| Seguridad | **Crítico** | EncryptedSharedPreferences + restringir cleartext | + certificate pinning + Timber release tree |
| Escalabilidad | Deficiente | Añadir Cast + offline básico | Multi-módulo + feature flags |
| Mantenibilidad | Deficiente | Hilt + Retrofit + tests | Compose + MVI + 70% coverage |
| Compliance | Aceptable con riesgo | Privacy Policy + Data Safety | Auditoría completa Play for TV |

---

## Conclusión y recomendación final

**LukiPlay Android está correctamente construido para lo que es: un MVP híbrido que carga el portal web y reproduce HLS en nativo.** Eso le permitió salir al aire rápido. **No es defendible como producto OTT comercial a 12 meses** porque:

1. No puede licenciar contenido premium (sin DRM real).
2. La experiencia TV no compite con el ecosistema (Leanback/Compose-TV es el estándar de facto).
3. La seguridad de sesión tiene hallazgos críticos triviales de explotar.
4. La velocidad de iteración futura está atada al WebView.

**Recomendación:** ejecutar la **migración híbrida estratégica** descrita en `ROADMAP_MIGRACION.md`, en ese orden, con un **gate de "Hardening" (Fase 0) hecho en las próximas 2 semanas** para tapar los hallazgos de seguridad sin esperar la migración completa.
