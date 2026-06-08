// mobile/MobileComposeActivity.kt
package com.luki.play.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.luki.play.data.profiles.ProfilesRepository
import com.luki.play.player.PlayerActivity
import com.luki.play.ui.LukiNavGraph
import com.luki.play.ui.theme.LukiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity nativa en Compose para móvil/tablet.
 *
 * Coexiste con [MobileMainActivity] (WebView) detrás de un feature flag:
 * `BuildConfig.NATIVE_HOME_ENABLED`. El [com.luki.play.ui.SplashActivity]
 * decide cuál lanzar.
 *
 * Reutiliza el player nativo abriendo [PlayerActivity] al pedir reproducción
 * desde el grafo Compose.
 */
@UnstableApi
@AndroidEntryPoint
class MobileComposeActivity : ComponentActivity() {

    @Inject lateinit var profilesRepository: ProfilesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LukiTheme {
                LukiNavGraph(
                    onLaunchPlayer = { config ->
                        startActivity(
                            PlayerActivity.newIntent(this, config)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    },
                    profilesRepository = profilesRepository,
                )
            }
        }
    }
}
