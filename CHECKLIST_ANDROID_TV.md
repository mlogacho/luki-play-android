# ✅ Checklist Android TV — Luki Play

> Estado auditado el **2026-06-15** contra el código real. El fix de `KEEP_SCREEN_ON`
> ya está **committeado en el repo como v13 / 1.0.10** (commits `f386367` + `cdfb9b5`);
> **pendiente: subir a Play Console un AAB con ese fix — usar el actual v14 / 1.0.11**
> (la v12 no incluye el fix).
> Leyenda: ✅ cumple (verificado) · ⚠️ cumple parcial / acción pendiente · ❌ no cumple · 🔍 requiere prueba manual

---

## 5.1 Manifest y configuración

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ✅ | `LEANBACK_LAUNCHER` declarado en actividad principal | `SplashActivity` tiene ambos intent-filters (LAUNCHER + LEANBACK_LAUNCHER). Confirmado con `aapt dump badging`: `leanback-launchable-activity`. |
| ✅ | `android.software.leanback` con `required="false"` | Manifest línea 51 + `aapt`: `uses-feature-not-required: android.software.leanback`. |
| ✅ | `android.hardware.touchscreen` con `required="false"` | Manifest 46-48 (incl. multitouch). Confirmado con `aapt`. También `screen.portrait/landscape` not-required (fix commit `38eccd8`, clave para no ser excluido). |
| ✅ | `android:banner` declarado en `<application>` | Apunta a `@drawable/banner_tv` (PNG **320×180** real) — verificado con `file`. El `tv_banner.xml` (shape/gradiente) se eliminó. Fix commit `0cd51a1`. |
| ✅ | `minSdkVersion ≤ 31` | `minSdk = 23` (build.gradle.kts:21). |
| ✅ | Soporte 64-bit (obligatorio desde 1 ago 2026) | `abiFilters`: armeabi-v7a, **arm64-v8a**, x86, **x86_64** (build.gradle.kts:31). |
| ✅ | Soporte 16 KB page sizes (obligatorio desde 1 ago 2026) | AGP **9.2.1** (≥ 8.5.1 alinea libs automáticamente) y **sin `.so` propias** en `app/src`. 🔍 Confirmar en Play Console → versión → "16 KB" check tras subir. |

## 5.2 Navegación y UX

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ✅ | Toda la UI navegable con D-pad | `DPAD_JS` nativo **eliminado** (commit `d8e5c74`): las teclas llegan solo vía `onKeyDown → dispatchKeyEvent` y la nav la maneja `useSpatialNavigation` web (2D + grafo PLAYER_NAV). 🔍 Validar fin a fin con control real (login → home → player → back). |
| ⚠️ | Estados de focus visibles | Player: ✅ anillo naranja (`navProp`/focus ring). 🔍 Auditar login/home/listas con control real. |
| ✅ | Orientación landscape en pantallas TV | `TvMainActivity` y `TvComposeActivity`: `android:screenOrientation="landscape"`. Activities portrait no excluyen TV (features not-required). |
| ✅ | Sin dependencia de gestos táctiles en TV | En TV el flujo va 100% por D-pad (zapping ◄►, botones prev/next con OK, FABs). El swipe existe solo como extra en móvil. |
| ⚠️ | Texto y controles legibles a 10 pies | Web escala TV 2.2× (`scaleSheet`). **Pero `TV_SCALE_JS` aplica `zoom=0.82`** (hack que además puede descuadrar `getBoundingClientRect` de la nav). **Acción: retirar/reemplazar el hack y validar legibilidad.** |

## 5.3 Reproducción de video

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ✅ | Reproductor optimizado para TV (sin overlays táctiles obligatorios) | HlsVideoPlayer (HLS.js), `controls:false`, overlays propios; en TV controles aparecen con OK y se auto-ocultan (2.5 s). |
| ✅ | Controles de reproducción accesibles con D-pad | Grafo `PLAYER_NAV` (foco inicial ►): prev/next canal, Info, Canales, Ir a, volumen, lock, back — todo con OK. Wrap-around último→primero. |
| ✅ | Prevenir Ambient Mode / screensaver durante reproducción | `TvMainActivity.onCreate` ahora hace `window.addFlags(FLAG_KEEP_SCREEN_ON)` → la TV no se atenúa/apaga durante la reproducción. (En árbol de trabajo, pendiente de rebuild a v13.) |
| ⚠️ | PIP (opcional) | `PlayerActivity` declara `supportsPictureInPicture=true`, pero el player nativo está deshabilitado (bridge → siempre web). Opcional: no bloquea aprobación. |

## 5.4 Performance

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| 🔍 | Arranque en frío < 3 s | Shell nativo arranca rápido, pero el contenido es web remoto (depende de red/EC2). **Medir en el Streamer:** `adb shell am start -W com.luki.play/.ui.SplashActivity` + tiempo a primer render. |
| ✅ | Streaming sin buffering excesivo | HLS.js afinado: `lowLatencyMode`, `maxBufferLength 8/15`, `liveSyncDurationCount 2`, recuperación de errores de red/media. |
| ✅ | Manejo de pérdida de conexión | Overlay de error nativo con "Reintentar" (códigos -2/-6/-7/-8 = red) + `hls.startLoad()` en errores fatales de red. |

## 5.5 Permisos

| Estado | Requisito | Evidencia |
|---|---|---|
| ✅ | Sin permisos que impliquen hardware no disponible en TV | Solo: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(+MEDIA_PLAYBACK)`, `WAKE_LOCK`. Ninguno implica features. |
| ✅ | `CAMERA` no declarado como required | No se declara el permiso; `uses-feature camera` explícitamente `required="false"`. |
| ✅ | Sin telefonía ni sensores móviles | No hay permisos de telefonía/SMS/ubicación; features declaradas not-required. |

---

## 🎯 Acciones (orden de prioridad)

### Hechas ✅
1. ~~Quitar `DPAD_JS` de `TvMainActivity`~~ → **hecho** (commit `d8e5c74`).
2. ~~Banner → `@drawable/banner_tv`~~ → **hecho** (commit `0cd51a1`, PNG 320×180 real).
3. ~~`FLAG_KEEP_SCREEN_ON` en TvMainActivity~~ → **hecho** (en árbol de trabajo; entra en el rebuild a v13).

### Pendientes
4. **Subir el AAB v14 / 1.0.11 a Play Console** (incluye el fix de `KEEP_SCREEN_ON` de v13 y el cifrado de sesión del bridge JS) y enviarlo como la versión a revisar para TV (la v12 en Console no incluye esos fixes).
5. **Play Console: activar el formato Android TV** (Formatos del dispositivo → Android TV → "Agregar") + subir **≥1 captura 1920×1080** y enviar a revisión de TV. Sin esto la tienda no ofrece la app a TVs aunque el AAB sea elegible.
6. Retirar/ajustar el hack `zoom=0.82` (`TV_SCALE_JS`) y **validar legibilidad 10-foot en TV/emulador real** (cambiarlo a ciegas puede descuadrar la nav y el layout).
7. Prueba manual solo-D-pad: login → home → player → zapping → back (foco visible, sin callejones).
8. Tras subir el próximo AAB: verificar en Console el check de 16 KB y "Funciones necesarias" = 0 requeridas.
