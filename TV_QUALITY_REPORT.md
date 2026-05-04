# TV_QUALITY_REPORT.md — Luki Play Android TV

> Informe de calidad para Android TV / Google TV  
> Agente: qa-validator | Fecha: 2024-05

---

## Resumen ejecutivo

| Categoría | Resultado | Observaciones |
|---|---|---|
| Launcher & Discovery | ✅ PASS | Banner + Leanback declarados |
| Navegación D-Pad | ✅ PASS | JS helper inyectado en TvMainActivity |
| Pantalla completa | ✅ PASS | Sticky immersive en Player + TV |
| Reproducción HLS | ✅ PASS | Media3 1.4.1 + ExoPlayer |
| Rotación / Config changes | ✅ PASS | configChanges en Manifest |
| Cleartext HTTP | ✅ PASS | network_security_config.xml solo 98.80.97.51 |
| DRM | ⚠️ SKIP | No implementado — contenido sin encriptar en v1 |
| PiP | ✅ PASS | supportsPictureInPicture=true en Manifest (móvil) |

---

## 1. Launcher & Discovery

- **Banner TV** → `drawable/banner_tv.png` declarado en `android:banner` ✅  
- **Leanback Launcher** → `LEANBACK_LAUNCHER` + `LAUNCHER` en SplashActivity ✅  
- **Touchscreen no requerido** → `android.hardware.touchscreen required=false` ✅

---

## 2. Navegación D-Pad

JS helper inyectado por `TvMainActivity.onPageFinished`:

```js
document.addEventListener('keydown', e => {
  const s = 200;
  if (e.key === 'ArrowUp')    window.scrollBy(0,-s);
  if (e.key === 'ArrowDown')  window.scrollBy(0, s);
  if (e.key === 'ArrowLeft')  window.scrollBy(-s,0);
  if (e.key === 'ArrowRight') window.scrollBy( s,0);
  if (e.key === 'Enter')      document.activeElement?.click();
}, true);
```

Key forwarding: `onKeyDown` → DPAD_* + ENTER dispatched al WebView.  
BACK → `webView.goBack()` o sistema. ✅

---

## 3. Pantalla completa

- **PlayerActivity** → `WindowInsetsController.hide(systemBars)` + fallback legacy ✅  
- **TvMainActivity** → `SYSTEM_UI_FLAG_IMMERSIVE_STICKY | FULLSCREEN | HIDE_NAVIGATION` ✅

---

## 4. Reproducción HLS

| Ítem | Detalle |
|---|---|
| Motor | ExoPlayer via Media3 1.4.1 |
| Protocolo | HLS (m3u8) — `media3-exoplayer-hls` |
| Superficie | `surface_view` (mejor para TV) |
| Buffering | `show_buffering="when_playing"` |
| Posición | Persistida en `SavedStateHandle` |
| keepScreenOn | En layout XML ✅ |
| DRM | `drmToken` en `StreamConfig` — NOT activado ⚠️ |

---

## 5. Config changes (rotación / teclado)

`configChanges="orientation|screenSize|keyboardHidden"` en todas las Activities.  
`PlayerViewModel` survives → posición restaurada sin rebuffering. ✅

---

## 6. Seguridad de red

Cleartext solo para `98.80.97.51` via `network_security_config.xml`.  
Todo lo demás requiere HTTPS. ✅

---

## 7. WebView — Calidad

| Ítem | Estado |
|---|---|
| JS habilitado | ✅ |
| DOM Storage | ✅ |
| Cookies persistentes | ✅ |
| File access | ❌ Deshabilitado |
| UA personalizado | `LukiPlay-Android/1.0.0` |
| Remote debugging | ✅ debug builds |
| URL interception | Solo `98.80.97.51` ✅ |

---

## 8. Checklist Google Play TV

| Requisito | Estado |
|---|---|
| `touchscreen required=false` | ✅ |
| `leanback required=false` | ✅ |
| Banner 320×180 | ✅ |
| LEANBACK_LAUNCHER | ✅ |
| App sin crash sin touch | ✅ |
| Sin permisos AV innecesarios | ✅ |

---

## 9. Pendientes para v2

| Ítem | Prioridad |
|---|---|
| Activar Widevine DRM | Alta |
| MediaSession para notificaciones | Media |
| Pruebas en Google TV físico | Alta |
| Migrar HTTP→HTTPS cuando el backend lo soporte | Alta |
| Escalar íconos PNG a densidades exactas | Baja |
