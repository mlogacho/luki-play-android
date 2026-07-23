import java.util.Properties

// app/build.gradle.kts — Módulo principal de Luki Play Android

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Crashlytics solo se activa si existe google-services.json (descargado de la
// consola de Firebase). Sin el archivo, el build sigue funcionando y los
// Timber.* de release son no-ops — así el repo compila en cualquier máquina.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}


android {
    namespace = "com.luki.play"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.luki.play"
        minSdk          = 23
        targetSdk       = 35
        versionCode     = 16
        versionName     = "1.0.13"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
            // Empaqueta los símbolos de depuración nativos en el AAB para que
            // Google Play pueda simbolizar los stack traces de crashes/ANR del
            // código nativo (.so). Elimina la advertencia de la Console.
            debugSymbolLevel = "FULL"
        }
    }

    val keystoreProps = Properties().also { props ->
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) props.load(propsFile.reader())
    }

    signingConfigs {
        create("release") {
            storeFile     = file("../keystore/luki-play-release.keystore")
            storePassword = keystoreProps.getProperty("KEYSTORE_PASS", "")
            keyAlias      = "luki-play-release"
            keyPassword   = keystoreProps.getProperty("KEY_PASS", "")
        }
    }

    buildTypes {
        debug {
            // Se mantiene el sufijo para pruebas locales
            applicationIdSuffix = ".debug"
            isDebuggable        = true
            buildConfigField("String",  "BASE_URL",     "\"https://lukiplay.com\"")
            buildConfigField("String",  "API_BASE_URL", "\"https://lukiplay.com\"")
            // Feature flags
            // El bloqueante de /auth/profiles ya no aplica: el selector de perfiles
            // salió del grafo (el portal no tiene esa función). Queda apagado
            // mientras las pantallas nativas alcanzan paridad de diseño con el
            // portal y falta QA en dispositivo real. Ponerlo en true para probar
            // el flujo nativo completo en local.
            buildConfigField("boolean", "NATIVE_HOME_ENABLED", "false")
            buildConfigField("boolean", "OFFLINE_DOWNLOADS_ENABLED", "true")
        }

        release {
            signingConfig      = signingConfigs.getByName("release")
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String",  "BASE_URL",     "\"https://lukiplay.com\"")
            buildConfigField("String",  "API_BASE_URL", "\"https://lukiplay.com\"")
            // En release el Compose nativo arranca apagado hasta rollout gradual
            buildConfigField("boolean", "NATIVE_HOME_ENABLED", "false")
            buildConfigField("boolean", "OFFLINE_DOWNLOADS_ENABLED", "false")
        }
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = true
        compose      = true
    }

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
    implementation(libs.bundles.media3.playback)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.security.crypto)
    implementation(libs.timber)

    // ── Firebase Crashlytics (telemetría) + Remote Config (feature flags) ────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    // ── Hilt (DI) ────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ── Networking (Retrofit + OkHttp + Moshi) ───────────────────────────────
    implementation(libs.bundles.network)
    ksp(libs.moshi.codegen)

    // ── Compose (móvil) ──────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Compose for TV ───────────────────────────────────────────────────────
    implementation(libs.bundles.tv)

    // ── Room (caché offline catálogo) ────────────────────────────────────────
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // ── WorkManager (descargas + recommendations) ────────────────────────────
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Chromecast (Cast Framework + Media3 Cast extension) ──────────────────
    implementation(libs.cast.framework)
    implementation(libs.media3.cast)

    // ── Tests ────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
