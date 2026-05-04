# API_INTEGRATION.md — Luki Play Android ↔ CMS API

> Guía de integración entre la app Android nativa y el backend de Luki Play.
> Fecha de descubrimiento de la API: 2026-05-04

---

## Arquitectura del servidor `98.80.97.51`

```
http://98.80.97.51
│
├── /home          → Portal web (Expo/React Native Web)  ← WebView carga esto
├── /cms           → Panel de administración CMS
├── /public/*      → API REST pública (sin auth)
├── /auth/*        → Autenticación JWT
├── /admin/*       → API admin (requiere rol)
└── /uploads/*     → Assets estáticos (logos, imágenes de canales)
```

---

## Mapa completo de la API (descubierto del JS bundle)

### Endpoints PÚBLICOS (sin token)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/public/canales` | Lista de canales activos |
| GET | `/public/sliders` | Banners/sliders del home |

**Ejemplo respuesta `/public/canales`:**
```json
[
  {
    "id": "a3f7a294-6532-4bca-a341-cb359f9ed894",
    "nombre": "TELERITMO",
    "logo": "/uploads/logos/5281c089-8286-4600-914b-f964dab1fb09.png",
    "detalle": "",
    "categoria": "Música",
    "tipo": "tv",
    "requiereControlParental": false
  }
]
```

**Logos:** `http://98.80.97.51` + `/uploads/logos/...`

---

### Autenticación (app móvil/TV)

| Método | Ruta | Body | Descripción |
|---|---|---|---|
| POST | `/auth/app/id-login` | `{idNumber, password, deviceId}` | Login con cédula |
| POST | `/auth/app/contract-login` | `{contractNumber, password, deviceId}` | Login con contrato |
| GET | `/auth/me` | — | Perfil usuario autenticado |
| POST | `/auth/refresh` | `{refreshToken}` | Renovar token |
| POST | `/auth/logout` | — | Cerrar sesión |
| POST | `/auth/app/request-password-otp` | `{email}` | Solicitar OTP recuperación |
| POST | `/auth/app/reset-with-otp` | `{email, otpCode, newPassword}` | Resetear con OTP |

**⚠️ Nota:** `deviceId` = `Settings.Secure.ANDROID_ID` del dispositivo Android.

---

### Streams (requieren Bearer token)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/public/canales/{id}/stream` | URL HLS del canal |
| POST | `/public/streams/start` | Inicia sesión de stream |
| GET | `/public/streams/{id}` | Estado del stream |
| POST | `/public/streams/{id}/heartbeat` | Keepalive del stream |

---

### CMS (admin)

| Método | Ruta | Body | Descripción |
|---|---|---|---|
| POST | `/auth/cms/login` | `{email, password, deviceId}` | Login admin |
| POST | `/auth/cms/send-recovery-code` | `{email}` | Recuperar contraseña CMS |
| POST | `/auth/cms/reset-with-code` | `{email, code, newPassword, confirmPassword}` | Resetear contraseña CMS |

---

## Integración Android — Flujo de autenticación

```
WebView carga http://98.80.97.51/home
    ↓ usuario hace login en el portal web
    ↓ el portal recibe el JWT del backend
    ↓
window.LukiNative.onLoginSuccess(JSON.stringify({
  userId:       "uuid-del-usuario",
  displayName:  "Marco L",
  accessToken:  "eyJhbGci...",   ← JWT que la app Android necesita
  refreshToken: "eyJhbGci..."    ← opcional, para renovar
}))
    ↓
LukiBridge.onLoginSuccess() guarda el token en SharedPreferences
    ↓
Ahora la app Android puede llamar endpoints protegidos nativamente
(ej: /public/canales/{id}/stream para obtener la URL HLS real)
```

---

## Integración con window.LukiNative — Contrato para el equipo frontend

El portal web debe llamar estos métodos en los momentos indicados:

### Al hacer login exitoso
```js
window.LukiNative?.onLoginSuccess(JSON.stringify({
  userId:       usuario.id,
  displayName:  usuario.nombre,
  accessToken:  respuesta.accessToken,
  refreshToken: respuesta.refreshToken  // opcional
}));
```

### Al presionar "Reproducir" en un canal
```js
// Obtener la URL del stream del backend primero
const streamResp = await fetch(`/public/canales/${canalId}/stream`, {
  headers: { Authorization: `Bearer ${accessToken}` }
});
const { url: streamUrl } = await streamResp.json();

// Lanzar el reproductor nativo
if (window.LukiNative) {
  window.LukiNative.playStream(JSON.stringify({
    url:         streamUrl,          // HLS m3u8
    title:       canal.nombre,
    poster:      `http://98.80.97.51${canal.logo}`,
    subtitleUri: canal.subtitulosUrl ?? null  // opcional
  }));
} else {
  // Fallback navegador web
  videoElement.src = streamUrl;
  videoElement.play();
}
```

### Al hacer logout
```js
window.LukiNative?.logout();
```

### Para saber si estás en la app nativa o en el navegador
```js
const isNativeApp = typeof window.LukiNative !== 'undefined';
const deviceInfo  = isNativeApp
  ? JSON.parse(window.LukiNative.getDeviceInfo())
  : { isTV: false, platform: 'web' };

// deviceInfo tiene:
// {
//   isTV:           bool,
//   label:          "Pixel 7" | "Chromecast with Google TV",
//   screenWidthDp:  number,
//   screenHeightDp: number,
//   supportsPip:    bool,
//   deviceId:       "abc123",    ← usar para /auth/app/*-login
//   platform:       "android",
//   apiBaseUrl:     "http://98.80.97.51"
// }
```

---

## Nota sobre el agente Gemini en el CMS

El CMS en `/cms/` tiene un agente Gemini integrado. Para garantizar compatibilidad:

1. **No modificar la estructura del JWT** — el Android la parsea directamente
2. **Mantener el campo `accessToken`** en la respuesta de login (no renombrar a `token` o `jwt`)
3. **El endpoint `/public/canales`** debe permanecer público (sin auth)
4. **Las URLs de logos** deben seguir el patrón `/uploads/logos/...` (rutas relativas al host)
5. **El endpoint `/public/canales/{id}/stream`** debe devolver `{ url: "https://..." }` con la URL HLS

---

## Canales activos en producción (2026-05-04)

| # | Canal | Categoría | Logo |
|---|---|---|---|
| 1 | TELERITMO | Música | `/uploads/logos/5281c089...` |
| 2 | RTS | General | `/uploads/logos/187c8388...` |
| 3 | CINEMA PLATINO | Cine | `/uploads/logos/90c2dda8...` |
| 4 | ECUADOR TV | Noticias | `/uploads/logos/29ae55eb...` |
| 5 | CINE MEXICANO | Cine | `/uploads/logos/eea38544...` |
| 6 | EWTN | General | `/uploads/logos/8962b5f5...` |
| 7 | TC | Noticias | `/uploads/logos/cfde7497...` |
| 8 | TELEAMAZONAS | Noticias | `/uploads/logos/117b1c6a...` |
| 9 | PANICO HD | Cine | `/uploads/logos/071bbad3...` |

---

## Archivos Android implementados

| Archivo | Rol |
|---|---|
| `util/Constants.kt` | Todas las rutas de la API |
| `util/LukiApiClient.kt` | Cliente HTTP nativo (canales, sliders, stream, auth) |
| `bridge/LukiBridge.kt` | Persiste JWT + expone `deviceId` vía `getDeviceInfo()` |
| `app/build.gradle.kts` | `BuildConfig.API_BASE_URL` = `http://98.80.97.51` |
