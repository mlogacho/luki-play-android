# Luki Play — Android & Google TV

> Aplicación híbrida WebView + Media3 ExoPlayer para la plataforma OTT Luki Play.  
> Compatible con **Android móvil** (minSdk 21) y **Android TV / Google TV** (Leanback).

---

## Estructura del proyecto

```
LukiPlay/
├── app/
│   └── src/main/
│       ├── java/com/luki/play/
│       │   ├── LukiApplication.kt          ← Initialización global (WebView debug)
│       │   ├── MainActivity.kt             ← Router TV↔Móvil
│       │   ├── bridge/
│       │   │   ├── LukiBridge.kt           ← window.LukiNative JS interface
│       │   │   └── BridgeMessage.kt        ← Sealed class de mensajes
│       │   ├── mobile/
│       │   │   └── MobileMainActivity.kt   ← WebView móvil
│       │   ├── tv/
│       │   │   └── TvMainActivity.kt       ← WebView TV + D-Pad
│       │   ├── player/
│       │   │   ├── PlayerActivity.kt       ← ExoPlayer fullscreen
│       │   │   ├── PlayerViewModel.kt      ← Estado + SavedStateHandle
│       │   │   ├── LukiPlayerManager.kt    ← Wrapper ExoPlayer
│       │   │   └── StreamConfig.kt         ← Parcelable config
│       │   ├── ui/
│       │   │   └── SplashActivity.kt       ← Pantalla inicial
│       │   ├── webview/
│       │   │   ├── WebViewConfig.kt        ← Configuración centralizada
│       │   │   └── LukiWebViewClient.kt    ← Client con error handling
│       │   └── util/
│       │       ├── Constants.kt            ← BASE_URL, delays
│       │       ├── DeviceUtils.kt          ← Impl DeviceUtilsContract
│       │       └── DeviceUtilsContract.kt  ← Interfaz TV/móvil
│       └── res/
│           ├── layout/                     ← Layouts XML
│           ├── values/                     ← colors, strings, themes
│           ├── mipmap-*/                   ← Launcher icons (adaptive)
│           ├── drawable/                   ← banner_tv, splash_logo, ic_launcher_*
│           └── xml/
│               └── network_security_config.xml
├── gradle/
│   └── libs.versions.toml                  ← Catálogo de versiones
├── build.gradle.kts                        ← Raíz
├── app/build.gradle.kts                    ← Módulo app
└── settings.gradle.kts
```

---

## Stack técnico

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.25 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| JVM target | 17 |
| Media3 (ExoPlayer) | 1.4.1 |
| AndroidX Lifecycle | 2.8.2 |
| AndroidX WebKit | 1.11.0 |

---

## Configuración del entorno

### URLs
| Variable | Valor |
|---|---|
| `BASE_URL` (debug + release) | `http://98.80.97.51/home` |

### Credenciales de prueba
```
Usuario:    1720289063
Contraseña: LukiTest123
```

### Cleartext HTTP
Solo permitido para `98.80.97.51` via `xml/network_security_config.xml`.

---

## JavaScript Bridge (`window.LukiNative`)

```js
// Lanzar reproductor HLS nativo
window.LukiNative.playStream(JSON.stringify({
  url: "http://98.80.97.51/stream.m3u8", title: "Canal HD", poster: "http://..."
}))

// Detener reproductor
window.LukiNative.stopStream()

// Info del dispositivo
const info = JSON.parse(window.LukiNative.getDeviceInfo())
// → { isTV, label, screenWidthDp, screenHeightDp, supportsPip }

// PiP (móvil, API 26+)
window.LukiNative.enterPip()

// Auth
window.LukiNative.onLoginSuccess(JSON.stringify({ userId: "42", displayName: "Marco" }))
window.LukiNative.logout()
```

---

## Compilar y ejecutar

```bash
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:assembleRelease  # release APK (requiere keystore)
./gradlew :app:installDebug     # instalar en dispositivo conectado
```

---

## DRM

⚠️ **Widevine NO activado** en esta versión. Campo `drmToken` en `StreamConfig` reservado para futuro.

---

## TV Quality — resumen

Ver [`TV_QUALITY_REPORT.md`](TV_QUALITY_REPORT.md) para el informe completo.

| Requisito | Estado |
|---|---|
| Sin pantallas en blanco | ✅ | D-Pad navegable | ✅ |
| Sin crash en rotación | ✅ | Fullscreen sin barras | ✅ |
| Banner TV declarado | ✅ | Launcher Leanback | ✅ |

---

*Luki Play © 2026 — Propietario. Todos los derechos reservados.*
