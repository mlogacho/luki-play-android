// tv/TvComposeActivity.kt
package com.luki.play.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.SessionState
import com.luki.play.player.PlayerActivity
import com.luki.play.tv.compose.TvNavGraph
import com.luki.play.ui.theme.LukiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entrypoint Android TV en Compose for TV. Reusa el catálogo + el detalle
 * (resuelve la URL de stream vía endpoint) y delega al [PlayerActivity]
 * para reproducción.
 */
@UnstableApi
@AndroidEntryPoint
class TvComposeActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin sesión → activación por QR; con sesión → catálogo.
        val startAtHome = authRepository.current() is SessionState.Authenticated
        setContent {
            LukiTheme {
                TvNavGraph(
                    startAtHome = startAtHome,
                    onLaunchPlayer = { config ->
                        startActivity(
                            PlayerActivity.newIntent(this, config)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    }
                )
            }
        }
    }
}
