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
    const val HOME   = "tv/home"
    const val DETAIL = "tv/detail/{channelId}"
    fun detail(channelId: String): String = "tv/detail/$channelId"
}

@Composable
fun TvNavGraph(
    onLaunchPlayer: (StreamConfig) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = TvRoutes.HOME) {
        composable(TvRoutes.HOME) {
            TvHomeScreen(
                onChannelClick = { ch -> navController.navigate(TvRoutes.detail(ch.id)) }
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
