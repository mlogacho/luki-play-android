// data/config/RemoteFeatureFlags.kt
package com.luki.play.data.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.luki.play.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FeatureFlags] respaldado por Firebase Remote Config.
 *
 * Dos garantías importantes:
 *
 *  1. **Funciona sin Firebase.** Si el build no incluyó `google-services.json`
 *     —el repo compila y corre en cualquier máquina sin él— no hay Remote
 *     Config y cada flag cae a su valor de `BuildConfig`. El comportamiento es
 *     entonces idéntico al de antes de introducir Remote Config.
 *
 *  2. **El default es el valor compilado.** Antes del primer `fetchAndActivate`
 *     (arranque recién instalado, o sin red) Remote Config devuelve el default
 *     que registramos aquí, que es justo el `BuildConfig`. Así el rollout nunca
 *     "enciende" algo por accidente: hace falta un valor explícito en la
 *     consola para cambiar la conducta.
 */
@Singleton
class RemoteFeatureFlags @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeatureFlags {

    private val remoteConfig: FirebaseRemoteConfig? by lazy { initRemoteConfig() }

    override val nativeHomeEnabled: Boolean
        get() = remoteConfig
            ?.getBoolean(FeatureFlags.KEY_NATIVE_HOME_ENABLED)
            ?: BuildConfig.NATIVE_HOME_ENABLED

    override fun refresh() {
        val config = remoteConfig ?: return
        config.fetchAndActivate()
            .addOnFailureListener { Timber.tag(TAG).w(it, "fetch de Remote Config falló") }
    }

    private fun initRemoteConfig(): FirebaseRemoteConfig? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        FirebaseRemoteConfig.getInstance().apply {
            // El default = el valor compilado, para que sin fetch la conducta
            // no cambie respecto a los BuildConfig de siempre.
            setDefaultsAsync(
                mapOf(FeatureFlags.KEY_NATIVE_HOME_ENABLED to BuildConfig.NATIVE_HOME_ENABLED)
            )
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    // En debug refrescamos siempre; en release, cada hora, el
                    // mínimo recomendado para no abusar de la cuota.
                    .setMinimumFetchIntervalInSeconds(
                        if (BuildConfig.DEBUG) 0 else MIN_FETCH_INTERVAL_SECONDS
                    )
                    .build()
            )
        }
    }.getOrElse {
        Timber.tag(TAG).w(it, "Remote Config no disponible; se usan los BuildConfig")
        null
    }

    private companion object {
        const val TAG = "FeatureFlags"
        const val MIN_FETCH_INTERVAL_SECONDS = 3600L
    }
}
