# Checklist de Calidad y Validación (QA Validator)

## Criterios de "Hecho"

- [x] **Arquitectura y Estructura:** Todos los scripts de gradle y librerías BOM se definen de acuerdo a lo planteado.
- [x] **Manifiesto de TV y Móvil:** `android.software.leanback` y `android.hardware.touchscreen` se encuentran en `required="false"`, y las actividades poseen ambos intent-filters.
- [x] **Tráfico en Texto Plano (HTTP):** Definido un `network_security_config.xml` exclusivo para `98.80.97.51` y el dominio Mux de pruebas.
- [x] **WebView Bridge:** `LukiBridge.kt` y `MainActivity.kt` integran la lectura de JSON JS a nativo correctamente.
- [x] **Reproductor HLS ExoPlayer:** Implementada la base con Media3 sin incluir DASH ni RTSP de forma innecesaria. Widevine y drmToken preparados como nullable.
- [x] **Navegación / UI TV-Aware:** Botón 'Back' en WebView implementado a través del dispatcher de la actividad.

## Pruebas Pendientes en Dispositivo
> **A revisar por Marco en Android Studio:**

1. [ ] Ejecutar `./gradlew assembleDebug` (sin warnings graves).
2. [ ] Validar que Splash fluye hacia MainActivity.
3. [ ] Validar que MainActivity carga el dashboard HTTP de Luki-Play.
4. [ ] Validar disparar evento bridge en la consola web para abrir el reproductor ExoPlayer.
5. [ ] Validar que los íconos de Outline/Focus funcionan si se testea en Android TV.
