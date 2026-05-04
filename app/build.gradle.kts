import java.util.Properties

// app/build.gradle.kts — Módulo principal de Luki Play Android

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}


android {
    namespace = "com.luki.play"
    compileSdk = 34

    defaultConfig {
        applicationId   = "com.luki.play"
        minSdk          = 21
        targetSdk       = 34
        versionCode     = 1
        versionName     = "1.0.0"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // FORZAR inclusión de todas las arquitecturas en un solo APK
        // Esto soluciona el error de "Split APKs" en dispositivos de TV y emuladores
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

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
            applicationIdSuffix = ".debug"
            isDebuggable        = true
            buildConfigField("String", "BASE_URL",     "\"http://98.80.97.51/home\"")
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
        viewBinding  = true
        buildConfig  = true
    }

    // ── ABI Splits ──────────────────────────────────────────────────────────
    // IMPORTANTE: Desactivar explícitamente y resetear para que no queden 
    // APKs residuales de configuraciones anteriores.
    splits {
        abi {
            isEnable = false
            reset()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
}
