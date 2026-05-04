# proguard-rules.pro — Reglas de ofuscación para Luki Play Android
# Aplicadas únicamente en el build release (isMinifyEnabled = true)

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
# Conservar todas las clases de Media3 para evitar errores en tiempo de ejecución
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── WebView JavaScript Bridge ─────────────────────────────────────────────────
# Los métodos anotados con @JavascriptInterface deben sobrevivir la ofuscación
-keepclassmembers class com.luki.play.bridge.LukiBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Kotlin / Corrutinas ───────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── AndroidX Lifecycle ────────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }

# ── Reglas generales de depuración ───────────────────────────────────────────
# Preservar información de línea para stack traces legibles en producción
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
