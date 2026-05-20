# TV Activation — Luki Play

Implementación del flujo **Device Authorization Grant (RFC 8628)** para login en Chromecast/Android TV sin teclado.

## Estructura

```
tv-activation/
├── backend/
│   ├── services/
│   │   └── device-code.service.js   # Gestión de códigos con TTL
│   └── routes/
│       └── tv-auth.routes.js        # 3 endpoints Express
└── web/
    └── activar.html                 # Página de activación (self-contained)
```

---

## Fase 1 — Backend (repo del servidor)

### 1. Instalar dependencia

```bash
npm install qrcode
```

### 2. Copiar archivos

```
backend/services/device-code.service.js  →  src/services/
backend/routes/tv-auth.routes.js         →  src/routes/
```

### 3. Registrar las rutas en `app.js` / `server.js`

```js
const tvAuth = require('./routes/tv-auth.routes');
app.use('/api/tv', tvAuth);
```

### 4. Variables de entorno

```env
API_BASE_URL=http://98.80.97.51    # o https://play.luki.tv en producción
WEB_BASE_URL=https://play.luki.tv
```

### Endpoints resultantes

| Método | Ruta | Quién llama | Para qué |
|--------|------|-------------|----------|
| `POST` | `/api/tv/device-code` | TV (WebView) | Genera código + QR |
| `GET`  | `/api/tv/poll?device_code=xxx` | TV (polling 5s) | Verifica si el usuario autorizó |
| `POST` | `/api/tv/activate` | Página `/activar` | Valida credenciales y autoriza |

---

## Fase 2 — Página web (repo del portal)

### Opción A — Página standalone

Copiar `web/activar.html` al directorio público del portal:

```
web/activar.html  →  public/activar.html
```

Accesible en: `https://play.luki.tv/activar`

### Opción B — Componente Expo/React

Si el portal usa Expo/React Native Web, crear `screens/ActivarScreen.js` con la misma lógica. El HTML sirve como referencia de diseño y flujo.

### Ajustar la URL de la API

En `activar.html`, línea:
```js
const API_BASE = '';  // vacío = mismo origen
```
Si la página y el API están en dominios distintos, cambia a:
```js
const API_BASE = 'https://play.luki.tv';
```

---

## Cómo funciona el flujo completo

```
TV                          Servidor                    Teléfono/PC
 │                              │                            │
 │── POST /api/tv/device-code ──▶│                            │
 │◀─ { device_code, user_code,  │                            │
 │     qr_data_url } ───────────│                            │
 │                              │                            │
 │  [Muestra QR y código ABC-1234 en pantalla]               │
 │                              │                            │
 │── GET /poll?device_code ─────▶│       Escanea QR o escribe URL
 │◀─ { status: "pending" } ─────│  ──── play.luki.tv/activar ▶│
 │                              │                            │
 │  (repite cada 5s)            │      [Ingresa ABC-1234]    │
 │                              │      [Ingresa cédula+clave]│
 │                              │◀─ POST /api/tv/activate ───│
 │                              │── valida con /auth/app/id-login
 │                              │── authorize(user_code, token)
 │                              │─▶ { success: true } ───────│
 │                              │                            │
 │── GET /poll ─────────────────▶│       [Pantalla: ¡TV vinculada!]
 │◀─ { status: "authorized",    │
 │     access_token } ──────────│
 │                              │
 │  [Guarda token, redirige al home]
```

---

## Producción — Redis (recomendado)

El servicio usa `Map` en memoria, lo que no sobrevive reinicios del proceso ni escala horizontalmente. Para producción reemplaza el store:

```js
// device-code.service.js — versión Redis
const redis = require('ioredis');
const client = new redis(process.env.REDIS_URL);

async function createDeviceCode() {
  const deviceCode = crypto.randomUUID();
  const userCode   = generateUserCode();
  const ttl        = CODE_TTL_MS / 1000;

  await client.set(`dc:${deviceCode}`, JSON.stringify({ userCode, status: 'pending' }), 'EX', ttl);
  await client.set(`uc:${userCode}`, deviceCode, 'EX', ttl);

  return { deviceCode, userCode, expiresAt: Date.now() + CODE_TTL_MS };
}
```

---

## Fase 3 — Pantalla TV (próximo paso)

La pantalla de login en el portal web (`/home`) debe detectar `isTV` via `window.LukiNative.getDeviceInfo()` y mostrar la pantalla de activación con el QR en lugar del formulario cédula/clave.

El flujo nativo en el TV:
1. Portal llama `POST /api/tv/device-code`
2. Renderiza QR + código + spinner "Esperando..."
3. Cada 5s llama `GET /api/tv/poll?device_code=xxx`
4. Si `status === 'authorized'` → llama `window.LukiNative.onLoginSuccess(JSON.stringify({ accessToken, userId }))`
