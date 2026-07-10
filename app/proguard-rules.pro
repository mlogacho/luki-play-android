# ProGuard rules for Luki Play

# Mantener clases principales
-keep class com.luki.play.** { *; }

# Mantener la interfaz Javascript de WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Mantener clases de Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Configuración por defecto
-keepattributes *Annotation*
-keepattributes JavascriptInterface

# Firebase Crashlytics referencia android.os.ProfilingTrigger (API 36) que no
# existe en compileSdk 35; solo lo usa en runtime si la API está disponible.
-dontwarn android.os.ProfilingTrigger
-dontwarn android.os.ProfilingTrigger$Builder
