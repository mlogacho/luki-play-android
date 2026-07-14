# Luki Play — Android & Google TV

> Aplicación **híbrida** para la plataforma OTT Luki Play: shell nativo Kotlin +
> WebView (`https://lukiplay.com/home`) para la experiencia principal, con
> reproductor **Media3/ExoPlayer** nativo y una capa nativa completa (Compose,
> Compose for TV, Hilt, Retrofit, Room, Cast, descargas offline y DRM Widevine)
> lista tras *feature flags* para un rollout gradual.
>
> Compatible con **Android móvil/tablet** (minSdk 23) y **Android TV / Google TV** (Leanback).

**Estado actual:** `versionCode 15` · `versionName 1.0.12` (publicada en Play el 2026-07-10, incluye Crashlytics y el fix del crash de arranque en Android ≤ 11) · última revisión de doc: **2026-07-14**.

---

## Arquitectura en una imagen

```
SplashActivity ─► RouterActivity ─┬─ isTV ─┬─ NATIVE_HOME=on ─► TvComposeActivity     (Compose for TV)
                                  │        └─ NATIVE_HOME=off ─► TvMainActivity        (WebView TV)   ← ENVÍO ACTUAL
                                  └─ móvil ─┬─ NATIVE_HOME=on ─► MobileComposeActivity  (Compose)
                                           └─ NATIVE_HOME=off ─► MobileMainActivity     (WebView)     ← ENVÍO ACTUAL

WebView  ──(window.LukiNative.playStream)──►  PlayerActivity (Media3 ExoPlayer, HLS/DASH, Widevine, Cast)
```

`RouterActivity` elige la Activity destino según `DeviceUtils.isTv()` y el flag
`BuildConfig.NATIVE_HOME_ENABLED`. **Hoy ese flag está en `false`** (debug y release),
por lo que la app publicada carga el portal web; la UI nativa Compose existe en el
código y se enciende cuando el backend exponga los endpoints que necesita (ver
[Feature flags](#feature-flags)).

---

## Stack técnico

| Componente | Versión |
|---|---|
| Android Gradle Plugin | **9.2.1** |
| Gradle wrapper | 9.4.1 |
| Kotlin | **2.2.10** |
| KSP | 2.2.10-2.0.2 |
| compileSdk / targetSdk | 35 |
| minSdk | **23** |
| JVM target | 17 |
| Media3 (ExoPlayer) | 1.4.1 |
| Hilt (DI) | 2.57.1 |
| Retrofit / OkHttp / Moshi | 2.11.0 / 4.12.0 / 1.15.1 |
| Room | 2.7.1 |
| Jetpack Compose (BOM) | 2024.10.01 |
| Compose for TV | tv-foundation `1.0.0-alpha11` · tv-material `1.0.0` |
| Cast Framework + media3-cast | 21.5.0 / 1.4.1 |
| WorkManager | 2.9.1 |
| AndroidX Lifecycle | 2.8.6 |
| AndroidX WebKit | 1.11.0 |
| Firebase BOM (Crashlytics) | 34.16.0 |

> Todas las versiones se gestionan en [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Capacidades (estado real)

| Área | Estado | Detalle |
|---|---|---|
| Reproductor Media3 | ✅ | HLS **y DASH**, `MediaSession`, persistencia de posición (`SavedStateHandle`) |
| DRM Widevine | ✅ cableado | `player/drm/WidevineProvider.kt` conectado al `MediaSource.Factory`; se activa cuando el stream trae `drmScheme=WIDEVINE` + `licenseUrl`; contenido clear (FTA) coexiste |
| Licencias offline | ✅ base | `player/drm/OfflineLicenseAcquirer.kt` |
| QoS analytics | ✅ | `player/qos/QoSAnalyticsListener.kt` (startup, rebuffer, bitrate, errores) |
| Chromecast | ✅ | `cast/` + `LukiCastOptionsProvider` (auto-discovery vía manifest) |
| Descargas offline | ✅ tras flag | `data/downloads/` (Media3 `DownloadService` + WorkManager), `OFFLINE_DOWNLOADS_ENABLED` |
| Home / Search / Detalle nativos | ✅ tras flag | `feature/home`, `feature/search`, `feature/detail` (Compose + Coil + Paging) |
| Android TV nativo | ✅ tras flag | `tv/compose/` (Compose for TV) + canal de recomendaciones (`tv/recommendations/`) |
| Multiperfil | ✅ | `data/profiles/`, `feature/profiles/` |
| Control parental (PIN) | ✅ | `data/parental/`, `feature/parental/` |
| Auth nativa | ✅ | Retrofit + `AuthInterceptor` + `TokenAuthenticator` (refresh), tokens en `EncryptedSharedPreferences`. La sesión solo se destruye si el servidor rechaza el refresh (400/401/403); fallos transitorios la conservan |
| Caché de catálogo | ✅ | Room (`data/catalog/db/`) |
| Crashlytics | ✅ | Firebase Crashlytics activo desde 1.0.12 (`CrashlyticsTree` reenvía WARN/ERROR de Timber como breadcrumbs). Se activa solo si existe `app/google-services.json` |
| Blindaje de arranque | ✅ | `webview/WebViewSupport` (detecta WebView ausente/viejo, mín. Chromium 80) + `BlankPageWatchdog` (página muda) → pantalla de error nativa en vez de crash/negro |

**~6.900 líneas Kotlin en `app/src/main`.** El WebView sigue siendo el *source of
truth* de la UI mientras `NATIVE_HOME_ENABLED=false`.

---

## Estructura del proyecto

```
luki-play-android/
├── app/src/main/java/com/luki/play/
│   ├── LukiApplication.kt              ← @HiltAndroidApp, WorkManager config, Timber/Crashlytics, WebView debug
│   ├── MainActivity.kt                 ← legacy/fallback WebView
│   ├── bridge/                         ← window.LukiNative (LukiBridge + BridgeMessage)
│   ├── cast/                           ← Chromecast (CastController, OptionsProvider)
│   ├── data/
│   │   ├── auth/                       ← AuthRepository, SecureTokenStore, AuthApi
│   │   ├── catalog/                    ← ChannelsRepository, Room DB, CatalogApi
│   │   ├── downloads/                  ← DownloadsRepository, LukiDownloadService
│   │   ├── network/                    ← AuthInterceptor, TokenAuthenticator, NetworkModule
│   │   ├── parental/                   ← ParentalControl (PIN)
│   │   └── profiles/                   ← ProfilesRepository, ProfilesApi
│   ├── feature/                        ← Compose UI: home, search, detail, downloads, parental, profiles
│   ├── mobile/                         ← MobileMainActivity (WebView) + MobileComposeActivity (nativo)
│   ├── player/                         ← PlayerActivity, LukiPlayerManager, StreamConfig, drm/, qos/
│   ├── tv/                             ← TvMainActivity (WebView) + TvComposeActivity + compose/ + recommendations/
│   ├── ui/                             ← SplashActivity, RouterActivity, NavGraph, theme/
│   ├── util/                           ← Constants, DeviceUtils, SecureStorage, LukiApiClient, CrashlyticsTree
│   └── webview/                        ← WebViewConfig, LukiWebViewClient, WebViewSupport, BlankPageWatchdog
├── app/google-services.json            ← config Firebase (Crashlytics); sin él, el build compila igual
├── app/src/debug/res/xml/network_security_config.xml   ← override cleartext SOLO debug
├── app/src/test/                       ← 8 suites de tests unitarios (JUnit/MockK/Turbine/MockWebServer)
├── app/src/androidTest/                ← HomeScreenSmokeTest (Compose UI)
├── gradle/libs.versions.toml           ← catálogo de versiones
├── tv-activation/                      ← helpers backend/web para activación TV (no es módulo Gradle)
├── play-store-assets/                  ← eliminar-cuenta.html (data deletion)
└── keystore/                           ← luki-play-release.keystore
```

---

## Feature flags

Definidos como `buildConfigField` en [`app/build.gradle.kts`](app/build.gradle.kts):

| Flag | debug | release | Efecto |
|---|---|---|---|
| `NATIVE_HOME_ENABLED` | `false` | `false` | `true` enruta a la UI nativa Compose (móvil y TV). **Apagado**: depende de `GET /auth/profiles`, que el backend aún no expone (devuelve 404). |
| `OFFLINE_DOWNLOADS_ENABLED` | `true` | `false` | Habilita la sección de descargas offline. Encendido en dev para pruebas. |

> Cambiar un flag a `true` **no** requiere tocar código: `RouterActivity` ya
> resuelve el destino en función de él.

---

## Configuración del entorno

| Variable | Valor | Origen |
|---|---|---|
| `BASE_URL` | `https://lukiplay.com/home` | `util/Constants.kt` + `buildConfigField` |
| `API_BASE_URL` | `https://lukiplay.com` | `util/Constants.kt` |
| `SERVER_HOST` | `lukiplay.com` | `util/Constants.kt` |

Rutas de la API (login, streams, canales, etc.) en `util/Constants.kt`; contrato
completo en [`API_INTEGRATION.md`](API_INTEGRATION.md).

---

## JavaScript Bridge (`window.LukiNative`)

Interfaz expuesta al WebView en [`bridge/LukiBridge.kt`](app/src/main/java/com/luki/play/bridge/LukiBridge.kt).

```js
// Reproductor HLS/DASH nativo (Widevine si el payload trae DRM)
window.LukiNative.playStream(JSON.stringify({
  url: "https://cdn.example.com/stream.m3u8",
  title: "Canal HD",
  poster: "https://…",
  subtitleUri: "https://…/subs.vtt",     // opcional
  subtitleMimeType: "text/vtt"           // opcional
}))
window.LukiNative.stopStream()

// Sesión / auth
window.LukiNative.onLoginSuccess(JSON.stringify({ userId, displayName, accessToken, refreshToken }))
const sesion = JSON.parse(window.LukiNative.getStoredSession())  // "{}" si no hay sesión
window.LukiNative.clearStoredSession()
window.LukiNative.logout()

// Dispositivo / UI
const info = JSON.parse(window.LukiNative.getDeviceInfo())
// → { isTV, label, screenWidthDp, screenHeightDp, supportsPip, deviceId, platform, apiBaseUrl }
window.LukiNative.enterPip()             // móvil, API 26+
window.LukiNative.dispatch(JSON.stringify({ type: "…" }))  // dispatcher genérico
```

> 🔒 **Nota de seguridad:** desde v14 (1.0.11) el bridge persiste los tokens
> **cifrados**: `onLoginSuccess`/`getStoredSession` usan el mismo `TokenStore`
> (`SecureTokenStore` sobre `EncryptedSharedPreferences`) que la capa nativa, con
> migración one-shot que copia y borra los tokens de las prefs planas legacy.
> Riesgo restante conocido: cualquier JS que corra en el portal (XSS, script de
> terceros) puede llamar `getStoredSession()` — mitigación prevista: migrar a
> `WebViewCompat.addWebMessageListener` con allowlist de origen + CSP estricta
> en el portal. Ver [Seguridad](#seguridad).

---

## Compilar y ejecutar

```bash
./gradlew :app:assembleDebug       # APK debug
./gradlew :app:bundleRelease       # AAB release (requiere keystore + local.properties)
./gradlew :app:installDebug        # instalar en dispositivo/emulador conectado
./gradlew test                     # tests unitarios (JVM)
./gradlew connectedAndroidTest     # tests instrumentados (requiere dispositivo)
```

### Firma (release)
`local.properties` en la raíz:
```properties
KEYSTORE_PASS=<password_keystore>
KEY_PASS=<password_clave>
```
El keystore vive en `keystore/luki-play-release.keystore` (alias `luki-play-release`).
Si las credenciales faltan, el build no rompe (fallback a cadena vacía).

---

## Seguridad

| Aspecto | Estado |
|---|---|
| Cleartext en release | ✅ **Prohibido** — `base-config cleartextTrafficPermitted="false"` |
| Cleartext en debug | Solo `98.80.97.51` y `test-streams.mux.dev` (override en `src/debug/`) |
| Mixed content WebView | `ALWAYS_ALLOW` en debug · ✅ **`NEVER_ALLOW` en release** |
| Cookies de terceros | ✅ Deshabilitadas — el portal es un solo dominio (`lukiplay.com`) |
| Tokens capa nativa | ✅ `EncryptedSharedPreferences` (`SecureTokenStore` / `SecureStorage`) |
| Tokens vía bridge JS | ✅ Cifrados desde v14 — mismo `TokenStore` que la capa nativa, con migración desde las prefs planas legacy |
| Superficie del bridge JS | ⚠️ `getStoredSession()` es invocable por cualquier JS del portal (XSS / scripts de terceros) — pendiente migrar a `addWebMessageListener` con allowlist de origen |
| URLs de stream (`playStream`) | ✅ Solo `https` en release (`http` se admite en debug para QA); el resto se rechaza con log |
| Logging | Timber; el bridge no loguea payloads (podían contener JWT / `licenseHeaders` DRM) y en release solo WARN/ERROR llegan a Crashlytics |
| File access WebView | ✅ Deshabilitado (`allowFileAccess=false`, `allowContentAccess=false`) |
| Símbolos nativos de crash | `debugSymbolLevel = "FULL"` en el AAB |

---

## DRM

**Widevine cableado y funcional** (`player/drm/WidevineProvider.kt` conectado en
`LukiPlayerManager.buildMediaSource`). Se activa por stream cuando el JSON de
`playStream` incluye `drmScheme` + `licenseUrl` (+ `licenseHeaders` opcionales);
el contenido *clear* (FTA/IPTV abierto) sigue reproduciéndose sin DRM. El nivel
L1/L3 lo negocia el dispositivo vía `FrameworkMediaDrm`.

> Requiere backend: los endpoints de stream deben devolver `licenseUrl` para el
> catálogo premium. Hoy los canales públicos van *in-the-clear*.

---

## TV Quality

Checklist vivo y auditado en [`CHECKLIST_ANDROID_TV.md`](CHECKLIST_ANDROID_TV.md).
Resumen: `LEANBACK_LAUNCHER` ✅ · banner `banner_tv` 320×180 ✅ · features
`required="false"` (touch/portrait/telephony/GPS) ✅ · nav D-pad por navegación
espacial web (el `DPAD_JS` nativo se eliminó) ✅ · `KEEP_SCREEN_ON` en reproducción ✅.

---

## Documentación relacionada

| Documento | Contenido |
|---|---|
| [`API_INTEGRATION.md`](API_INTEGRATION.md) | Contrato REST + bridge con el backend |
| [`PLAN_MIGRACION_NATIVA_MOVIL.md`](PLAN_MIGRACION_NATIVA_MOVIL.md) | Plan por sprints de la migración nativa móvil (vigente, 2026-07-10) |
| [`POSTMORTEM_APP_NO_ABRE_ANDROID11.md`](POSTMORTEM_APP_NO_ABRE_ANDROID11.md) | Postmortem del crash de arranque en Android ≤ 11 (resuelto en 1.0.12) |
| [`CHECKLIST_ANDROID_TV.md`](CHECKLIST_ANDROID_TV.md) | Checklist de calidad Android TV (auditado 2026-06-15; el AAB pendiente que menciona ya se publicó como v15) |
| [`GOOGLE_PLAY_PUBLISHING_GUIDE.md`](GOOGLE_PLAY_PUBLISHING_GUIDE.md) | Textos y config de Play Console |
| [`AUDITORIA_LUKIPLAY_ANDROID.md`](AUDITORIA_LUKIPLAY_ANDROID.md) | Auditoría histórica (2026-05-29) — baseline |
| [`ROADMAP_MIGRACION.md`](ROADMAP_MIGRACION.md) | Plan de migración F0–F5 (mayormente ejecutado) |
| [`TV_QUALITY_REPORT.md`](TV_QUALITY_REPORT.md) | Informe TV histórico (superado por el checklist) |

---

*Luki Play © 2026 — Propietario. Todos los derechos reservados.*
</content>
</invoke>
