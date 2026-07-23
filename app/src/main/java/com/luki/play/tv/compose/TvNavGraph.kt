// tv/compose/TvNavGraph.kt
package com.luki.play.tv.compose

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luki.play.feature.detail.ChannelDetailScreen
import com.luki.play.player.StreamConfig

object TvRoutes {
    const val ACTIVATION = "tv/activation"
    const val HOME       = "tv/home"
    const val ACCOUNT    = "tv/account"
    const val DETAIL     = "tv/detail/{channelId}"
    fun detail(channelId: String): String = "tv/detail/$channelId"
}

/**
 * @param startAtHome true si ya hay sesión (la TV entra directo al catálogo);
 *   false arranca en la activación por QR para conseguir una.
 */
@Composable
fun TvNavGraph(
    onLaunchPlayer: (StreamConfig) -> Unit,
    startAtHome: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (startAtHome) TvRoutes.HOME else TvRoutes.ACTIVATION,
    ) {
        composable(TvRoutes.ACTIVATION) {
            TvActivationScreen(
                onAuthenticated = {
                    navController.navigate(TvRoutes.HOME) {
                        popUpTo(TvRoutes.ACTIVATION) { inclusive = true }
                    }
                },
            )
        }
        composable(TvRoutes.HOME) {
            TvHomeScreen(
                onChannelClick = { ch -> navController.navigate(TvRoutes.detail(ch.id)) },
                onOpenAccount = { navController.navigate(TvRoutes.ACCOUNT) },
            )
        }
        composable(TvRoutes.ACCOUNT) {
            TvAccountScreen(
                onLoggedOut = {
                    // Sin sesión → de vuelta a la activación por QR, limpiando el
                    // back stack (no debe poderse volver al catálogo con "atrás").
                    navController.navigate(TvRoutes.ACTIVATION) {
                        popUpTo(TvRoutes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(TvRoutes.DETAIL) {
            ChannelDetailScreen(
                onPlay = onLaunchPlayer,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
