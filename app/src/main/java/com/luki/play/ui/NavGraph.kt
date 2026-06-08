// ui/NavGraph.kt
package com.luki.play.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luki.play.data.profiles.ProfilesRepository
import com.luki.play.feature.detail.ChannelDetailScreen
import com.luki.play.feature.downloads.DownloadsScreen
import com.luki.play.feature.home.HomeScreen
import com.luki.play.feature.parental.ParentalPinScreen
import com.luki.play.feature.profiles.ProfilePickerScreen
import com.luki.play.feature.search.SearchScreen
import com.luki.play.player.StreamConfig
import androidx.media3.common.util.UnstableApi

/** Rutas de navegación tipadas. */
object LukiRoutes {
    const val PICKER    = "picker"
    const val HOME      = "home"
    const val SEARCH    = "search"
    const val DOWNLOADS = "downloads"
    const val DETAIL    = "detail/{channelId}"
    fun detail(channelId: String): String = "detail/$channelId"
}

/**
 * Navegación principal móvil.
 *
 * Estructura:
 *  - Si no hay perfil activo → PICKER es startDestination.
 *  - Una vez elegido → HOME y resto del flujo.
 *  - Parental gate se inyecta como modal sobre cualquier destino vía un
 *    `Dialog` controlado por estado al nivel del NavGraph (evita una ruta
 *    dedicada que rompería el back-stack).
 */
@UnstableApi
@Composable
fun LukiNavGraph(
    onLaunchPlayer: (StreamConfig) -> Unit,
    profilesRepository: ProfilesRepository,
    navController: NavHostController = rememberNavController(),
) {
    var parentalGate by remember { mutableStateOf<ParentalGateRequest?>(null) }
    val triggerParentalGate: (onVerified: () -> Unit, onDismissed: () -> Unit) -> Unit =
        { onVerified, onDismissed ->
            parentalGate = ParentalGateRequest(onVerified, onDismissed)
        }

    val startDestination =
        if (profilesRepository.activeProfileId.value.isNullOrBlank()) LukiRoutes.PICKER
        else LukiRoutes.HOME

    NavHost(navController = navController, startDestination = startDestination) {

        composable(LukiRoutes.PICKER) {
            ProfilePickerScreen(
                onProfileChosen = {
                    navController.navigate(LukiRoutes.HOME) {
                        popUpTo(LukiRoutes.PICKER) { inclusive = true }
                    }
                },
                onRequestParentalGate = { onVerified ->
                    triggerParentalGate(onVerified) { /* dismiss */ }
                },
            )
        }

        composable(LukiRoutes.HOME) {
            HomeScreen(
                onChannelClick = { ch -> navController.navigate(LukiRoutes.detail(ch.id)) },
                onOpenSearch   = { navController.navigate(LukiRoutes.SEARCH) },
            )
        }

        composable(LukiRoutes.SEARCH) {
            SearchScreen(
                onChannelClick = { ch -> navController.navigate(LukiRoutes.detail(ch.id)) },
            )
        }

        composable(LukiRoutes.DOWNLOADS) {
            DownloadsScreen()
        }

        composable(LukiRoutes.DETAIL) {
            ChannelDetailScreen(
                onPlay = onLaunchPlayer,
                onBack = { navController.popBackStack() },
                onRequestParentalGate = triggerParentalGate,
            )
        }
    }

    // Modal parental gate por encima del NavHost
    parentalGate?.let { req ->
        Dialog(onDismissRequest = {
            req.onDismissed()
            parentalGate = null
        }) {
            ParentalPinScreen(onSuccess = {
                req.onVerified()
                parentalGate = null
            })
        }
    }
}

private data class ParentalGateRequest(
    val onVerified: () -> Unit,
    val onDismissed: () -> Unit,
)
