# proguard-rules.pro — Reglas de ofuscación para Luki Play Android
# Aplicadas únicamente en el build release (isMinifyEnabled = true)

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── WebView JavaScript Bridge ─────────────────────────────────────────────────
-keepclassmembers class com.luki.play.bridge.LukiBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Kotlin / Corrutinas ───────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── AndroidX Lifecycle ────────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }

# ── Timber ────────────────────────────────────────────────────────────────────
# Timber usa reflexión para el tag automático; mantener nombres de clases.
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }

# ── AndroidX Security Crypto (EncryptedSharedPreferences) ────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── Reglas generales de depuración ───────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
