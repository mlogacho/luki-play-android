# LukiPlay iOS bootstrap

Esta carpeta contiene una base de app iOS para LukiPlay con:
- WebView principal apuntando a https://lukiplay.com/home
- Bridge nativo `LukiNative` para play/stop/login/logout/device info/session
- Player fullscreen con AVPlayer

## Estructura

- LukiPlay/App/LukiPlayApp.swift
- LukiPlay/UI/RootView.swift
- LukiPlay/UI/PlayerScreen.swift
- LukiPlay/Web/WebViewContainer.swift
- LukiPlay/Core/Bridge/*
- LukiPlay/Core/Session/*
- LukiPlay/Core/Device/*
- LukiPlay/Core/Models/*
- LukiPlay/Player/*

## Como levantar en Xcode

1. En Xcode, crea un proyecto nuevo iOS App llamado `LukiPlay` (SwiftUI + Swift).
2. Borra el archivo de vista por defecto que crea Xcode.
3. Copia los archivos de `ios/LukiPlay` dentro del target.
4. En Signing & Capabilities configura tu Team y Bundle Identifier.
5. Ejecuta en simulador o dispositivo iPhone.

## Bridge JS esperado

En WebView se inyecta automaticamente un shim que expone:

- `window.LukiNative.playStream(payload)`
- `window.LukiNative.stopStream()`
- `window.LukiNative.onLoginSuccess(payload)`
- `window.LukiNative.logout()`
- `window.LukiNative.getDeviceInfo()` -> Promise
- `window.LukiNative.getStoredSession()` -> Promise
- `window.LukiNative.getDeviceInfo(true)` -> String JSON (modo legacy)
- `window.LukiNative.getStoredSession(true)` -> String JSON (modo legacy)

### Ejemplo play stream

```js
window.LukiNative.playStream({
  type: "play_stream",
  url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
  title: "Canal Demo"
});
```

## Nota de compatibilidad

En Android `getDeviceInfo()` y `getStoredSession()` son sincronos.

En iOS quedan disponibles ambos estilos:

- Promise (recomendado):

```js
const info = await window.LukiNative.getDeviceInfo();
const session = await window.LukiNative.getStoredSession();
```

- Legacy tipo Android (string JSON inmediato, basado en cache nativa):

```js
const infoRaw = window.LukiNative.getDeviceInfo(true); // "{...}"
const info = JSON.parse(infoRaw || "{}");

const sessionRaw = window.LukiNative.getStoredSession(true); // "{...}"
const session = JSON.parse(sessionRaw || "{}");
```

Adicionalmente `playStream` soporta:

- Objeto con `type: "play_stream"`
- Objeto sin `type` pero con `url`
- String JSON
- URL directa (`"https://...m3u8"`)
