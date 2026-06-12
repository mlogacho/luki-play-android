# ✅ Checklist Android TV — Luki Play

> Estado auditado el **2026-06-12** contra el código real (AAB v11 / 1.0.8).
> Leyenda: ✅ cumple (verificado) · ⚠️ cumple parcial / acción pendiente · ❌ no cumple · 🔍 requiere prueba manual

---

## 5.1 Manifest y configuración

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ✅ | `LEANBACK_LAUNCHER` declarado en actividad principal | `SplashActivity` tiene ambos intent-filters (LAUNCHER + LEANBACK_LAUNCHER). Confirmado con `aapt dump badging`: `leanback-launchable-activity`. |
| ✅ | `android.software.leanback` con `required="false"` | Manifest línea 51 + `aapt`: `uses-feature-not-required: android.software.leanback`. |
| ✅ | `android.hardware.touchscreen` con `required="false"` | Manifest 46-48 (incl. multitouch). Confirmado con `aapt`. También `screen.portrait/landscape` not-required (fix commit `38eccd8`, clave para no ser excluido). |
| ⚠️ | `android:banner` declarado en `<application>` | Declarado (línea 85) **pero apunta a `tv_banner.xml` (shape/gradiente)**. Existe `banner_tv.png` **320×180** sin usar. **Acción: cambiar a `@drawable/banner_tv`.** |
| ✅ | `minSdkVersion ≤ 31` | `minSdk = 23` (build.gradle.kts:21). |
| ✅ | Soporte 64-bit (obligatorio desde 1 ago 2026) | `abiFilters`: armeabi-v7a, **arm64-v8a**, x86, **x86_64** (build.gradle.kts:31). |
| ✅ | Soporte 16 KB page sizes (obligatorio desde 1 ago 2026) | AGP **9.2.1** (≥ 8.5.1 alinea libs automáticamente) y **sin `.so` propias** en `app/src`. 🔍 Confirmar en Play Console → versión → "16 KB" check tras subir. |

## 5.2 Navegación y UX

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ⚠️ | Toda la UI navegable con D-pad | Hay **DOS sistemas en conflicto**: `DPAD_JS` nativo (TvMainActivity, recorrido lineal) vs `useSpatialNavigation` web (2D + grafo PLAYER_NAV). Causa foco errático. **Acción: eliminar `DPAD_JS` y dejar solo el reenvío `onKeyDown→dispatchKeyEvent` + nav web.** |
| ⚠️ | Estados de focus visibles | Player: ✅ anillo naranja (`navProp`/focus ring). 🔍 Auditar login/home/listas con control real. |
| ✅ | Orientación landscape en pantallas TV | `TvMainActivity` y `TvComposeActivity`: `android:screenOrientation="landscape"`. Activities portrait no excluyen TV (features not-required). |
| ✅ | Sin dependencia de gestos táctiles en TV | En TV el flujo va 100% por D-pad (zapping ◄►, botones prev/next con OK, FABs). El swipe existe solo como extra en móvil. |
| ⚠️ | Texto y controles legibles a 10 pies | Web escala TV 2.2× (`scaleSheet`). **Pero `TV_SCALE_JS` aplica `zoom=0.82`** (hack que además puede descuadrar `getBoundingClientRect` de la nav). **Acción: retirar/reemplazar el hack y validar legibilidad.** |

## 5.3 Reproducción de video

| Estado | Requisito | Evidencia / Acción |
|---|---|---|
| ✅ | Reproductor optimizado para TV (sin overlays táctiles obligatorios) | HlsVideoPlayer (HLS.js), `controls:false`, overlays propios; en TV controles aparecen con OK y se auto-ocultan (2.5 s). |
| ✅ | Controles de reproducción accesibles con D-pad | Grafo `PLAYER_NAV` (foco inicial ►): prev/next canal, Info, Canales, Ir a, volumen, lock, back — todo con OK. Wrap-around último→primero. |
| ⚠️ | Prevenir Ambient Mode / screensaver durante reproducción | `WAKE_LOCK` declarado, pero **`TvMainActivity` NO setea `FLAG_KEEP_SCREEN_ON`** (Mobile solo lo hace en fullscreen). **Acción: `window.addFlags(FLAG_KEEP_SCREEN_ON)` en TvMainActivity (o al reproducir).** |
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

## 🎯 Acciones pendientes (orden de prioridad)

1. **Quitar `DPAD_JS` de `TvMainActivity`** (conflicto de navegación — riesgo principal de rechazo UX).
2. **Banner → `@drawable/banner_tv`** (PNG 320×180 real).
3. **`FLAG_KEEP_SCREEN_ON` en TvMainActivity** (evitar screensaver durante reproducción).
4. Retirar hack `zoom=0.82` (`TV_SCALE_JS`) y validar legibilidad 10-foot.
5. Play Console: **activar factor de forma Android TV** + ≥1 captura 1920×1080 (distribución; sin esto la tienda no ofrece la app a TVs aunque el AAB sea elegible).
6. Prueba manual solo-D-pad: login → home → player → zapping → back (foco visible, sin callejones).
7. Tras subir el próximo AAB: verificar en Console el check de 16 KB y "Funciones necesarias" = 0 requeridas.
