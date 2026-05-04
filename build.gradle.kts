// build.gradle.kts — Raíz del proyecto
// Solo declara los plugins disponibles; NO los aplica aquí.
// Cada módulo (:app) aplica los que necesita.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}
