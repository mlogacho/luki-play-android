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

| Parámetro | Valor |
|---|---|
| AGP | 8.5.2 |
| Kotlin | 1.9.25 |
| Media3 BOM | 1.4.1 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |
| JVM target | 17 |
| BASE_URL | `http://98.80.97.51/home` |
| ABI splits | arm64-v8a, x86_64 |
| R8 / Minify | release=true |
| ViewBinding | true |
| BuildConfig | true |
| JS Bridge name | `window.LukiNative` |
| DRM Widevine | NO — `drmToken` nullable reservado para v2 |
| MainActivity rol | Router puro (no layout, no setContentView) |
| MobileMainActivity | WebView + LukiWebViewClient + LukiBridge + double-back |
| TvMainActivity | WebView + D-Pad JS helper + fullscreen + key forwarding |

## Bloqueos / preguntas para Marco

- [ ] El portal web en `98.80.97.51` debe llamar `window.LukiNative.playStream(...)` para activar el reproductor nativo. ¿Hay fecha para integrar esa llamada desde el frontend?
- [ ] ¿Se requiere soporte de subtítulos (CEA-608/708) para la v1?
- [ ] ¿Keystore de firma para Play Store ya existe o se necesita crear uno?
