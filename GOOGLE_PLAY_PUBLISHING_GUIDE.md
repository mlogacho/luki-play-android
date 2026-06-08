# Guía de Publicación Luki Play - Google Play Console

Este documento contiene los textos y configuraciones exactas para cumplir con los requisitos de Google Play para dispositivos Móviles y Android TV.

## 1. Acceso a Aplicaciones (Login de Prueba)
*Ubicación: Contenido de la aplicación > Acceso a aplicaciones*

Google requiere credenciales para revisar el flujo de streaming.

**Nombre de la instrucción:** Acceso de Revisión para Luki Play (Móvil y TV)

**Usuario / ID:** `[INSERTAR_CEDULA_O_ID_AQUI]`  
**Contraseña:** `[INSERTAR_PASSWORD_AQUI]`

**Instrucciones adicionales:**
> "Luki Play is a streaming service for Android and Android TV. To review the application, use the provided credentials to log in. Once inside, you can navigate through the live TV channels and VOD content using either a touchscreen (Mobile) or a D-Pad / Remote Control (Android TV). The login process is required to access the streaming features and API-driven content. No real payment information is required for this test account."

---

## 2. Descripción de la Aplicación
*Ubicación: Ficha de Play Store principal*

**Descripción corta:**
> "Streaming premium para dispositivos móviles y Smart TV con navegación optimizada."

**Descripción larga:**
> "Luki Play ofrece una experiencia de streaming premium optimizada para dispositivos móviles y Smart TV (Android TV / Google TV). Disfruta de una navegación fluida con control remoto mediante una interfaz Leanback diseñada para la mejor experiencia en pantalla grande. Nuestra tecnología Media3 asegura una reproducción estable de canales en vivo y contenido bajo demanda en cualquier resolución."

---

## 3. Configuración para Android TV (Leanback)
*Ubicación: Configuración avanzada > Factores de forma > Android TV*

Asegúrate de marcar lo siguiente:
- **Navegación:** Seleccionar "La aplicación es completamente navegable mediante un control remoto (D-Pad)."
- **Banner de TV:** El sistema usará el banner definido en el código (`@drawable/tv_banner`). ✅ Verificado: el `AndroidManifest.xml` declara `android:banner="@drawable/tv_banner"`.
- **Capturas de Pantalla:** Subir al menos 4 imágenes en resolución **1920x1080** (pueden ser capturas del emulador).

---

## 4. Política de Privacidad
*Ubicación: Contenido de la aplicación > Política de privacidad*

Debe incluir estos puntos en la web de destino:
- Luki Play recolecta el ID del dispositivo (Device ID) para gestionar las sesiones de streaming.
- No compartimos datos personales con terceros.
- Cumplimiento con las políticas de Google Play sobre "Living Room Devices".

---

## 5. Especificaciones Técnicas (v1.0.2)
- **VersionCode:** 3 ✅ (`app/build.gradle.kts`)
- **VersionName:** 1.0.2 ✅
- **Target SDK:** 35 ✅
- **Min SDK:** 23 (elevado desde 21 por requisito de `androidx.security:security-crypto`; sigue cubriendo Android TV)
- **ABIs compatibles:** armeabi-v7a, arm64-v8a, x86, x86_64. ✅
- **Formato de subida:** `.aab` (Android App Bundle).

---

## 6. Comandos para Generar el Bundle
Ejecuta estos comandos en la terminal antes de subir:

```bash
./gradlew clean
./gradlew :app:bundleRelease
```

El archivo se generará en:  
`app/build/outputs/bundle/release/app-release.aab`
