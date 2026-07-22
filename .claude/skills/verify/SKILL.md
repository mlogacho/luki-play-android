---
name: verify
description: Receta para verificar cambios de Luki Play en emulador — build, instalación, sembrado de sesión y evaluación del bridge JS vía CDP.
---

# Verificar Luki Play en emulador

## Build y lanzamiento

- `adb`/`emulator` no están en PATH: usar `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` y `$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe`.
- AVDs disponibles: `Pixel_8` (móvil), `Television_720p` (TV).
- Build: `.\gradlew.bat :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Para construir una versión vieja en un `git worktree`, copiar `local.properties` **y** `gradle/gradle-daemon-jvm.properties` (no versionado; sin él la build cae al Java 11 del PATH y falla).
- El build debug usa `applicationIdSuffix = ".debug"` → paquete `com.luki.play.debug`, pero las clases mantienen `com.luki.play`. Lanzar con:
  `adb shell am start -n com.luki.play.debug/com.luki.play.ui.SplashActivity`
- Flujo: SplashActivity → RouterActivity → MobileMainActivity (carga `BASE_URL/login`) o TvMainActivity (carga `BASE_URL`).

## Sembrar / inspeccionar sesión (build debug)

- Prefs legacy planas: `shared_prefs/luki_prefs.xml` (claves `luki_access_token`, `luki_refresh_token`, `luki_user_id`, `luki_display_name`).
- Almacén cifrado: `shared_prefs/luki_secure_prefs.xml` (EncryptedSharedPreferences; solo se ven keysets Tink y blobs).
- Acceso: `adb shell "run-as com.luki.play.debug cat shared_prefs/..."`. Para escribir: `adb push` a `/data/local/tmp/` y `run-as ... cp`. Con la app detenida (`am force-stop`).
- En Git Bash, prefijar `MSYS_NO_PATHCONV=1` a los `adb push/pull` con rutas `/data/...`.

## Evaluar JS en el WebView (superficie del bridge)

El debug build activa `setWebContentsDebuggingEnabled`. Para llamar `window.LukiNative.*` como lo haría la web:

1. `PID=$(adb shell pidof com.luki.play.debug)`
2. `adb forward tcp:9222 localabstract:webview_devtools_remote_$PID` (rehacer tras cada reinicio de la app: el socket lleva el pid).
3. WebSocket a `webSocketDebuggerUrl` de `http://localhost:9222/json` + `Runtime.evaluate`. Con python `websocket-client` pasar `suppress_origin=True` (Chromium rechaza el Origin con 403).

Hay un helper listo en scratchpads anteriores: `cdp_eval.py` (re-crearlo es ~20 líneas).

## Flujos que vale la pena conducir

- Migración legacy → cifrado: sembrar `luki_prefs.xml`, lanzar, llamar `getStoredSession()` vía CDP. La migración es *lazy*: solo corre al primer acceso a SecureStorage; en móvil la página `/login` no llama al bridge por sí sola. Logs Timber: tag `SecureStorage`, mensajes "migrando/migración".
- `logout()` vía CDP: debe limpiar sesión y recargar login sin crash (onLogout se postea al main thread).
- Navegación externa: `window.location.href='https://example.com'` → logcat `LukiWebViewClient ... blocking external URL`.
