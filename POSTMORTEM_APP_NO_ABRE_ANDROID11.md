# Postmortem — "La app no abre" (crash de arranque en Android ≤ 11)

**Estado:** Resuelto y confirmado en campo · **Fecha de cierre:** 2026-07-10
**Versiones afectadas:** 1.0.8 (code 11) → 1.0.10 (code 13) · **Versión con el fix:** 1.0.12 (code 15)
**Severidad:** Crítica — la app era inutilizable en todo dispositivo con Android 11 o inferior.

---

## 1. Resumen ejecutivo

Entre el 12 de junio y el 10 de julio de 2026, la app publicada en Google Play **crasheaba instantáneamente al abrirse en cualquier dispositivo con Android ≤ 11** (TVs Riviera, head units de auto, teléfonos como el Redmi 8). En Android 12+ funcionaba con normalidad, por lo que el desarrollo y las pruebas —hechas en equipos modernos— nunca lo vieron.

La causa fue un archivo de recurso de **6 líneas**: un vector XML sin contenido (`ic_splash_empty.xml`) usado como ícono del splash screen. La librería de compatibilidad de splash lo infla manualmente **solo en Android 11 y anteriores**, y al no encontrar ningún trazo (`<path>`) lanza una excepción que mata a la app antes de dibujar el primer frame.

Como no existía telemetría de crashes, el único síntoma visible fueron reportes vagos de usuarios: *"la app no se abre"*, *"se queda la pantalla en negro"*.

---

## 2. Impacto

| Dimensión | Detalle |
|---|---|
| Usuarios afectados | 100% de los que tienen Android 6–11: TVs Riviera, head units (YT9218), boxes AOSP, teléfonos viejos. Núcleo del parque de dispositivos del servicio |
| Duración | ~28 días en producción (1.0.8 publicada ~2026-06-12 → 1.0.12 publicada 2026-07-10) |
| Síntoma | Crash inmediato al tocar "Abrir": la app muere en el splash, en algunos launchers se percibe como "pantalla negra que no avanza" |
| Detección | Reportes manuales de usuarios (sin Crashlytics no había visibilidad) |

---

## 3. Línea de tiempo

| Fecha | Evento |
|---|---|
| 2026-05-20 | Commit `78e3659` (rework de branding) crea `ic_splash_empty.xml` **sin `<path>`** como ícono "invisible" del splash |
| 2026-06-12 | Se publica **1.0.8 (code 11)** — el bug entra a producción. Pasa QA porque las pruebas se hacen en Android 12+ |
| 2026-06-12 → 06-24 | 1.0.9 y **1.0.10 (code 13)** heredan el bug |
| ~2026-07-08 | Usuarios reportan: la app instalada desde Play no abre (head unit de auto, TV Riviera, luego un teléfono) |
| 2026-07-09 | Diagnóstico: se extraen del Redmi 8 los APKs **exactos que entrega Play** y se reproducen en emulador Android 11 → crash `no path defined`. Prueba A/B con el vector corregido → abre. Causa raíz confirmada |
| 2026-07-09 | Fix (`a9a2085`) + blindaje adicional del arranque (`743121a`) + Crashlytics (`7897d9e`) → **1.0.12 (code 15)** verificada en emuladores Android 11 y 16 |
| 2026-07-10 | 1.0.12 sube a Play, pasa revisión el mismo día y se publica al 100% |
| 2026-07-10 | **Confirmación en campo:** el head unit del auto (el caso reportado más difícil) abre, muestra login y reproduce canales |

---

## 4. Causa raíz (explicación técnica)

### El archivo

`app/src/main/res/drawable/ic_splash_empty.xml` — así quedó tras el rework de branding:

```xml
<!-- ANTES (roto): un vector sin ningún trazo -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108" />
```

La intención era legítima: el splash de Android 12+ exige un "ícono animado" (`windowSplashScreenAnimatedIcon` en `themes.xml`), pero el diseño de Luki Play muestra el logo por otro medio, así que se apuntó ese atributo a un vector **vacío** para que no dibujara nada.

### Por qué crashea — y solo en Android ≤ 11

La app usa `androidx.core:core-splashscreen`, la librería de Google que emula el splash de Android 12 en versiones anteriores. Su comportamiento es distinto según la versión del sistema:

- **Android 12+ (API 31+):** el splash lo dibuja **el sistema operativo**. El recurso lo procesa el framework, que tolera un vector vacío. Nada falla.
- **Android ≤ 11 (API < 31):** el sistema no tiene splash nativo, así que **la librería infla el vector manualmente** con `VectorDrawable`. El parser de `VectorDrawable` exige al menos un elemento `<path>`; si no lo encuentra lanza:

```
android.content.res.Resources$NotFoundException: File res/drawable/ic_splash_empty.xml
Caused by: org.xmlpull.v1.XmlPullParserException: Binary XML file line #2: <vector> tag requires viewportWidth > 0 ... no path defined
```

Esto ocurre en `SplashActivity.onCreate()`, **antes del primer frame** → crash instantáneo, sin ventana, sin mensaje. En launchers de TV/head unit se ve como "toqué Abrir y no pasó nada" o una pantalla negra fugaz.

### Por qué nadie lo vio antes

1. **El build no lo detecta:** `aapt` valida que el XML esté bien formado, no que el vector tenga sentido semántico. Compila limpio.
2. **Los equipos de desarrollo y prueba eran Android 12+**, donde el archivo ni siquiera se infla por la ruta rota.
3. **No había telemetría:** sin Crashlytics, un crash en campo era invisible salvo que un usuario lo reportara — y los reportes llegaron sin stack trace, como "no abre".

Es el patrón clásico de bug de compatibilidad: la rama de código que falla **solo existe en dispositivos que el equipo no tiene sobre el escritorio**.

### El fix

Un `<path>` transparente que cubre el viewport — el vector sigue siendo invisible, pero ya es válido para el parser:

```xml
<!-- DESPUÉS (fix, commit a9a2085) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path
        android:pathData="M0,0h108v108H0z"
        android:fillColor="#00000000" />
</vector>
```

---

## 5. Cómo se diagnosticó (método)

El diagnóstico descartó hipótesis con evidencia en vez de adivinar — quedó documentado porque el método importa más que este bug puntual:

1. **Hipótesis descartadas primero:** portal caído (curl → servidor sano), WebView viejo (el Redmi tenía WebView 149, actual), regresión del allowlist de hosts de v13. Los builds locales debug **y** release abrían bien en emulador — la contradicción clave.
2. **La pregunta correcta:** si mi AAB funciona y "el de Play" no… ¿estoy probando los mismos bytes? No: Play entrega **splits** por dispositivo, no el APK universal.
3. **Prueba con los bytes reales:** `adb shell pm path com.luki.play` en el Redmi → extracción de los splits exactos que Play instaló → `adb install-multiple` en un emulador **Android 11** → crash reproducido al primer intento, con stack trace completo en `logcat -b crash`.
4. **A/B:** mismos splits + el vector corregido → abre. Causa raíz confirmada, no supuesta.

**Lección de método:** reproducir con el artefacto real (los splits de Play) en la API real (Android 11) fue lo que rompió el caso. Probar "algo parecido" (build local, emulador moderno) daba falsos negativos.

---

## 6. Hallazgo secundario (mismo diagnóstico)

El bundle JS del portal `lukiplay.com` usa sintaxis moderna (*optional chaining* `?.`) que requiere **Chromium ≥ 80** (feb 2020) para parsearse. En WebViews más viejos el bundle lanza `SyntaxError` y la página queda **negra sin ningún error de red** — un segundo modo de fallo con el mismo síntoma que el crash del splash.

- Mitigación en la app (ya en 1.0.12): `WebViewSupport` verifica la versión del motor antes de cargar y muestra pantalla accionable ("Abrir Play Store") si no alcanza; `BlankPageWatchdog` detecta página en blanco a los 12 s y ofrece reintentar.
- Mitigación de fondo (pendiente, repo del portal): bajar el target de transpilación del build web de Expo.

---

## 7. Qué cambió para que no se repita

| Medida | Estado | Dónde |
|---|---|---|
| **Crashlytics activo** — todo crash en campo llega con stack trace, modelo y versión de Android; verificado end-to-end con crash de prueba | ✅ en 1.0.12 | `LukiApplication`, `CrashlyticsTree` |
| **Arranque blindado** — try/catch al inflar el WebView con pantalla de respaldo accionable (D-pad friendly); guards de lifecycle | ✅ en 1.0.12 | `TvMainActivity`, `MobileMainActivity`, `WebViewSupport` |
| **Watchdog de página en blanco** — la "pantalla negra eterna" ya no existe como estado final | ✅ en 1.0.12 | `BlankPageWatchdog` |
| **Matriz de apertura garantizada** — todo release debe abrir en emuladores API 23/30/35 + Redmi 8 antes de subir a Play | 📋 definida | [`PLAN_MIGRACION_NATIVA_MOVIL.md`](PLAN_MIGRACION_NATIVA_MOVIL.md) §2 |
| **Smoke de apertura en CI** — arrancar la app en emulador API 23/30/35 y verificar frame no-negro en cada merge | 📋 Sprint 0 del plan | ídem §5 |
| **Rollout gradual** (10%→50%→100%) con freno por crash-free | 📋 política desde el próximo release | ídem §2 |

**Notas operativas aprendidas** (para el que opere esto después):
- Para probar lo que Play entrega de verdad: extraer los splits del dispositivo (`pm path` + `pull`) o generar los splits del AAB con `bundletool build-apks` + `install-apks`.
- Para forzar un crash de prueba de Crashlytics: `am crash <PID>` — con el nombre de paquete (`am crash com.luki.play`) mata al **renderer sandbox del WebView**, no al proceso principal, y Crashlytics no registra nada.
- Los drafts de release en Play Console "retienen" bundles anteriores: si un release nuevo convive con un code viejo retenido, la Console rechaza con *"Ningún usuario recibirá este APK…"* — quitar el bundle retenido del draft.

---

## 8. Lecciones aprendidas

1. **Un recurso "decorativo" puede ser un single point of failure del arranque.** Todo lo que toca el camino Splash → primer frame es código crítico, aunque sean 6 líneas de XML.
2. **Compatibilidad no se hereda del build verde:** lo que compila y corre en Android 15 puede morir en Android 11 por ramas de librería que solo existen allí. La matriz de dispositivos mínima no es negociable.
3. **Sin telemetría, un bug crítico vive semanas.** Este crash estuvo 28 días en producción y se diagnosticó por fotos de usuarios. Con Crashlytics habría sido un issue con stack trace el primer día.
4. **Reproducir con el artefacto real** (splits de Play, API afectada) es la diferencia entre confirmar una causa y suponerla.
5. **El parque de dispositivos del negocio es viejo por diseño** (TVs baratas, head units): el piso real de la app es Android 6–11 con motores web congelados. Es el argumento central del plan de migración nativa.
