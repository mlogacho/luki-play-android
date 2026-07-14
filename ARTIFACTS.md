# Bitácora de artefactos — Luki-Play Android

Cada agente actualiza esta tabla tras generar archivos.

## Convenciones
- `[x]` entregado y validado
- `[~]` en progreso
- `[ ]` pendiente

## Entregables

| Agente | Archivo | Estado | Notas |
|---|---|---|---|
| architect | settings.gradle.kts | [x] | |
| architect | build.gradle.kts (root) | [x] | |
| architect | app/build.gradle.kts | [x] | kotlin-parcelize + savedstate añadidos |
| architect | gradle.properties | [x] | |
| architect | gradle/libs.versions.toml | [x] | kotlin-parcelize + viewmodel-savedstate añadidos |
| architect | proguard-rules.pro | [x] | |
| manifest-resources | AndroidManifest.xml | [x] | LukiApplication, 5 Activities, PiP, banner |
| manifest-resources | drawable/banner_tv.png | [x] | PNG generado |
| manifest-resources | mipmap adaptive icons | [x] | PNG en mdpi..xxxhdpi + XML anydpi-v26 |
| manifest-resources | values/colors.xml | [x] | luki_error, luki_warning, luki_success añadidos |
| manifest-resources | values/strings.xml | [x] | player_retry, player_error_no_url añadidos |
| manifest-resources | values/themes.xml | [x] | |
| manifest-resources | xml/network_security_config.xml | [x] | cleartext 98.80.97.51 |
| webview-bridge | webview/WebViewConfig.kt | [x] | configuración centralizada |
| webview-bridge | webview/LukiWebViewClient.kt | [x] | error handling, URL interception |
| webview-bridge | bridge/LukiBridge.kt | [x] | window.LukiNative |
| webview-bridge | util/DeviceUtilsContract.kt | [x] | interfaz para tv-mobile-ui |
| player-media3 | player/PlayerActivity.kt | [x] | fullscreen, BroadcastReceiver, retry |
| player-media3 | player/PlayerViewModel.kt | [x] | SavedStateHandle, uiState sealed |
| player-media3 | player/LukiPlayerManager.kt | [x] | reescrito con callback correcto |
| player-media3 | layout/activity_player.xml | [x] | PlayerView + error overlay |
| tv-mobile-ui | LukiApplication.kt | [x] | WebView debugging en debug builds |
| tv-mobile-ui | MainActivity.kt | [x] | router TV↔Móvil, no layout |
| tv-mobile-ui | mobile/MobileMainActivity.kt | [x] | WebView + bridge + double-back |
| tv-mobile-ui | tv/TvMainActivity.kt | [x] | WebView + D-Pad JS + fullscreen |
| tv-mobile-ui | splash/SplashActivity.kt | [x] | apunta a MainActivity |
| tv-mobile-ui | util/DeviceUtils.kt | [x] | impl completa DeviceUtilsContract + createImpl() |
| tv-mobile-ui | util/Constants.kt | [x] | |
| tv-mobile-ui | layout/activity_main.xml | [x] | layout WebView genérico (legacy) |
| tv-mobile-ui | layout/activity_mobile_main.xml | [x] | WebView + loading + error overlay |
| tv-mobile-ui | layout/activity_tv_main.xml | [x] | WebView TV + focus D-Pad + error overlay |
| tv-mobile-ui | layout/activity_splash.xml | [x] | |
| qa-validator | TV_QUALITY_REPORT.md | [x] | |
| qa-validator | README.md | [x] | |

## Decisiones técnicas (append only)

> **Nota (2026-07-03):** esta tabla registraba el andamiaje inicial (v1). Los valores
> vigentes están abajo; los originales se conservan tachados como bitácora histórica.

| Parámetro | Valor vigente (2026-07-14) | Valor original (v1) |
|---|---|---|
| AGP | **9.2.1** | ~~8.5.2~~ |
| Kotlin | **2.2.10** (KSP 2.2.10-2.0.2) | ~~1.9.25~~ |
| Media3 BOM | 1.4.1 | 1.4.1 |
| compileSdk / targetSdk | **35 / 35** | ~~34~~ |
| minSdk | **23** | ~~21~~ |
| versionCode / Name | **15 / 1.0.12** (publicada 2026-07-10) | ~~3 / 1.0.2~~ |
| JVM target | 17 | 17 |
| BASE_URL | **`https://lukiplay.com/home`** | ~~`http://98.80.97.51/home`~~ |
| ABI filters | armeabi-v7a, arm64-v8a, x86, x86_64 (splits off) | ~~arm64-v8a, x86_64~~ |
| R8 / Minify | release=true | true |
| ViewBinding / BuildConfig / Compose | true / true / **true** | true / true / — |
| JS Bridge name | `window.LukiNative` | `window.LukiNative` |
| DRM Widevine | **Cableado** (`WidevineProvider`, se activa con `drmScheme`+`licenseUrl`) | ~~NO — reservado para v2~~ |
| DI | **Hilt 2.57.1** | ~~ninguno~~ |
| HTTP | **Retrofit/OkHttp/Moshi** (+ auth interceptor + refresh) | ~~HttpURLConnection manual~~ |
| MainActivity rol | legacy/fallback; el router real es `RouterActivity` | Router puro |
| MobileMainActivity | WebView + LukiWebViewClient + LukiBridge + double-back | igual |
| TvMainActivity | WebView + nav espacial web (DPAD_JS eliminado) + KEEP_SCREEN_ON | ~~D-Pad JS helper~~ |

## Bloqueos / preguntas para Marco

- [ ] El portal web en `98.80.97.51` debe llamar `window.LukiNative.playStream(...)` para activar el reproductor nativo. ¿Hay fecha para integrar esa llamada desde el frontend?
- [ ] ¿Se requiere soporte de subtítulos (CEA-608/708) para la v1?
- [ ] ¿Keystore de firma para Play Store ya existe o se necesita crear uno?
