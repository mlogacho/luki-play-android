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
