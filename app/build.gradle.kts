import java.util.Properties

// app/build.gradle.kts — Módulo principal de Luki Play Android

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}


android {
    // Namespace debe coincidir con el paquete base del código fuente
    namespace = "com.luki.play"
    compileSdk = 34

    defaultConfig {
        applicationId   = "com.luki.play"
        minSdk          = 21
        targetSdk       = 34
        versionCode     = 1
        versionName     = "1.0.0"

        // Soporte para vectores en API < 21 mediante AppCompat
        vectorDrawables.useSupportLibrary = true

        // Nombre legible de la app (sobreescrito por strings.xml en runtime)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Firma de release ────────────────────────────────────────────────────────
    // Las credenciales se leen de local.properties para NO hardcodearlas en el repo.
    // Ver keystore/KEYSTORE_INFO.md para instrucciones completas.
    val keystoreProps = Properties().also { props ->
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) props.load(propsFile.reader())
    }

    signingConfigs {
        create("release") {
            storeFile     = file("../keystore/luki-play-release.keystore")
            storePassword = (keystoreProps["KEYSTORE_PASS"] as? String) ?: "LukiPlay2024!"
            keyAlias      = "luki-play-release"
            keyPassword   = (keystoreProps["KEY_PASS"] as? String)      ?: "LukiPlay2024!"
        }
    }

    buildTypes {
        debug {
            // Sufijo para instalar debug y release simultáneamente en el mismo dispositivo
            applicationIdSuffix = ".debug"
            isDebuggable        = true

            // Portal web que carga el WebView
            buildConfigField("String", "BASE_URL",     "\"http://98.80.97.51/home\"")
            // URL raíz de la API REST (sin trailing slash)
            buildConfigField("String", "API_BASE_URL", "\"http://98.80.97.51\"")
        }

        release {
            signingConfig      = signingConfigs.getByName("release")
            isMinifyEnabled    = true
            isShrinkResources  = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "BASE_URL",     "\"http://98.80.97.51/home\"")
            buildConfigField("String", "API_BASE_URL", "\"http://98.80.97.51\"")
        }
    }

    buildFeatures {
        viewBinding  = true   // Acceso tipo-seguro a vistas sin findViewById
        buildConfig  = true   // Genera la clase BuildConfig con los campos personalizados
    }

    // ── ABI Splits ──────────────────────────────────────────────────────────
    // Genera APKs separados por arquitectura para reducir tamaño de descarga.
    // arm64-v8a cubre la gran mayoría de dispositivos modernos (incluidos Android TV).
    // x86_64 cubre emuladores y Chromebooks.
    splits {
        abi {
            isEnable        = true
            reset()                               // Limpia la lista por defecto
            include("arm64-v8a", "x86_64")
            isUniversalApk  = false               // No generar APK universal
        }
    }

    // ── Compatibilidad de compilación ────────────────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ── Media3 ────────────────────────────────────────────────────────────────
    // Se han movido las versiones directamente a libs.versions.toml para evitar
    // el fallo de resolución del BOM en algunos entornos.
    implementation(libs.media3.exoplayer)       // Núcleo del reproductor
    implementation(libs.media3.exoplayer.hls)   // Soporte para streams HLS (m3u8)
    implementation(libs.media3.ui)              // PlayerView, SubtitleView, etc.
    implementation(libs.media3.session)         // MediaSession / integración con sistema
    implementation(libs.media3.common)          // Tipos compartidos entre artefactos

    // ── AndroidX Core & Activity ─────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)  // Necesario para 'by viewModels()'
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // ── WebKit ────────────────────────────────────────────────────────────────
    // Proporciona WebViewCompat y ProxyController para gestión avanzada del WebView
    implementation(libs.androidx.webkit)

    // ── Corrutinas ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
}
