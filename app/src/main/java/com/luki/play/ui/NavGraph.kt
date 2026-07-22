// ui/NavGraph.kt
package com.luki.play.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luki.play.data.auth.AuthRepository
import com.luki.play.data.auth.SessionState
import com.luki.play.feature.detail.ChannelDetailScreen
import com.luki.play.feature.downloads.DownloadsScreen
import com.luki.play.feature.favorites.FavoritesScreen
import com.luki.play.feature.home.HomeScreen
import com.luki.play.feature.login.LoginScreen
import com.luki.play.feature.login.RecoverPasswordScreen
import com.luki.play.feature.login.SessionViewModel
import com.luki.play.feature.parental.ParentalPinScreen
import com.luki.play.feature.search.SearchScreen
import com.luki.play.player.StreamConfig
import androidx.media3.common.util.UnstableApi

/** Rutas de navegación tipadas. */
object LukiRoutes {
    const val LOGIN     = "login"
    const val RECOVER   = "recover"
    const val HOME      = "home"
    const val SEARCH    = "search"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val DETAIL    = "detail/{channelId}"
    fun detail(channelId: String): String = "detail/$channelId"
}

/**
 * Navegación principal móvil.
 *
 * Estructura:
 *  - Sin sesión (Anonymous) → LOGIN es startDestination.
 *  - Con sesión → HOME, igual que el portal.
 *  - Parental gate se inyecta como modal sobre cualquier destino vía un
 *    `Dialog` controlado por estado al nivel del NavGraph (evita una ruta
 *    dedicada que rompería el back-stack).
 *
 * Sin selector de perfiles: el portal no tiene esa función (sus destinos son
 * Inicio / Buscar / Mi Lista, y `profile` es la cuenta del usuario, no un
 * selector). PICKER se construyó de forma especulativa y además bloqueaba
 * todo el arranque nativo, porque depende de `GET /auth/profiles`, que el
 * backend responde 404. ProfilePickerScreen queda sin usar a la espera de
 * decidir si se elimina.
 */
@UnstableApi
@Composable
fun LukiNavGraph(
    onLaunchPlayer: (StreamConfig) -> Unit,
    /** Abre el portal web — flujos que aún no tienen pantalla nativa. */
    onOpenPortal: () -> Unit,
    authRepository: AuthRepository,
    navController: NavHostController = rememberNavController(),
) {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val loggedOut by sessionViewModel.loggedOut.collectAsStateWithLifecycle()

    LaunchedEffect(loggedOut) {
        if (loggedOut) {
            // Limpia todo el back-stack: tras cerrar sesión no debe poder
            // volverse a HOME con el botón atrás.
            navController.navigate(LukiRoutes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            sessionViewModel.consumeLoggedOut()
        }
    }

    var parentalGate by remember { mutableStateOf<ParentalGateRequest?>(null) }
    val triggerParentalGate: (onVerified: () -> Unit, onDismissed: () -> Unit) -> Unit =
        { onVerified, onDismissed ->
            parentalGate = ParentalGateRequest(onVerified, onDismissed)
        }

    val startDestination =
        if (authRepository.current() is SessionState.Anonymous) LukiRoutes.LOGIN
        else LukiRoutes.HOME

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
        ) {

            composable(LukiRoutes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(LukiRoutes.HOME) {
                            popUpTo(LukiRoutes.LOGIN) { inclusive = true }
                        }
                    },
                    onForgotPassword = { navController.navigate(LukiRoutes.RECOVER) },
                    // Activación y solicitud de acceso siguen viviendo en el
                    // portal: no hay pantalla nativa todavía, así que se abren
                    // ahí en vez de dejar un enlace muerto.
                    onActivateAccount = onOpenPortal,
                    onRequestAccess   = onOpenPortal,
                )
            }

            composable(LukiRoutes.RECOVER) {
                RecoverPasswordScreen(
                    // popBackStack en vez de navigate: el login sigue en el stack
                    // y así no se apilan instancias al ir y volver.
                    onBackToLogin = { navController.popBackStack(LukiRoutes.LOGIN, inclusive = false) },
                )
            }

            composable(LukiRoutes.HOME) {
                HomeScreen(
                    onChannelClick = { ch -> navController.navigate(LukiRoutes.detail(ch.id)) },
                    onLogout       = { sessionViewModel.logout() },
                )
            }

            composable(LukiRoutes.SEARCH) {
                SearchScreen(
                    onChannelClick = { ch -> navController.navigate(LukiRoutes.detail(ch.id)) },
                )
            }

            composable(LukiRoutes.FAVORITES) {
                FavoritesScreen(
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

        if (showBottomBar) {
            LukiBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            // Comportamiento de pestañas: una sola instancia de
                            // cada una y sin acumular back-stack al saltar entre
                            // ellas; el botón atrás vuelve a Inicio, no recorre
                            // el historial de pestañas visitadas.
                            popUpTo(LukiRoutes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
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

/** Rutas que muestran la barra de pestanas. */
private val TAB_ROUTES = setOf(LukiRoutes.HOME, LukiRoutes.SEARCH, LukiRoutes.FAVORITES)

private data class ParentalGateRequest(
    val onVerified: () -> Unit,
    val onDismissed: () -> Unit,
)
