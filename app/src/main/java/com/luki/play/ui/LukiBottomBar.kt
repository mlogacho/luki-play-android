// ui/LukiBottomBar.kt
package com.luki.play.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barra de pestañas del portal (`app/(app)/(tabs)/_layout.tsx`).
 *
 * Ojo con su historia: el portal la **oculta cuando corre en web**
 * (`Platform.OS === 'web'`), y web es justo lo que hoy se sirve dentro del
 * WebView de Android. O sea que este diseño existe en el repo del portal
 * pero ningún usuario de la app lo ha visto nunca. Se recupera aquí porque
 * sin él Buscar y Mi Lista se quedan sin ninguna vía de acceso.
 */
@Composable
fun LukiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BottomBarPalette.Background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BottomBarPalette.TopBorder)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // El inset evita que la barra de gestos del sistema tape las
                // etiquetas; en RN lo resolvía el safe-area del navegador.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(60.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BOTTOM_TABS.forEach { tab ->
                BottomTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onNavigate(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    tab: BottomTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint =
        if (selected) BottomBarPalette.Active else BottomBarPalette.Inactive

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) tab.iconSelected else tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.sp,
        )
    }
}

private data class BottomTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

/** Inicio / Buscar / Mi Lista, en el orden y con los rótulos del portal. */
private val BOTTOM_TABS = listOf(
    BottomTabItem(
        route = LukiRoutes.HOME,
        label = "Inicio",
        icon = Icons.Outlined.Home,
        iconSelected = Icons.Filled.Home,
    ),
    BottomTabItem(
        route = LukiRoutes.SEARCH,
        label = "Buscar",
        icon = Icons.Outlined.Search,
        iconSelected = Icons.Filled.Search,
    ),
    BottomTabItem(
        route = LukiRoutes.FAVORITES,
        label = "Mi Lista",
        icon = Icons.AutoMirrored.Outlined.List,
        iconSelected = Icons.AutoMirrored.Filled.List,
    ),
)

/** Colores exactos de `tabBarStyle` en el portal. */
private object BottomBarPalette {
    val Background = Color(0xFF140026)
    val TopBorder = Color(0x6660269E)   // rgba(96, 38, 158, 0.4)
    val Active = Color(0xFFFFB800)
    val Inactive = Color(0xFFB07CC6)
}
