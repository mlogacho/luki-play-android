// tv/compose/TvAccountScreen.kt
package com.luki.play.tv.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.auth.UserProfile
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette

/**
 * Cuenta de TV (10-foot). Antes la TV no ofrecía forma de cerrar sesión; esta
 * pantalla muestra la identidad del titular y un cierre de sesión con paso de
 * confirmación (evita el logout accidental de un solo clic del mando). Usa la
 * línea gráfica de marca ([LukiPalette]), no el tema Material genérico.
 */
@Composable
fun TvAccountScreen(
    onLoggedOut: () -> Unit,
    viewModel: TvAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    val logoutFocus = remember { FocusRequester() }

    // Auto-foco: al entrar cae en "Cerrar sesión"; al abrir la confirmación se
    // reubica en "Cancelar" (ambos comparten el mismo FocusRequester), porque al
    // salir de composición el botón anterior pierde el foco.
    LaunchedEffect(confirming) { runCatching { logoutFocus.requestFocus() } }

    LukiGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Mi cuenta",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(28.dp))

            when (val phase = state.phase) {
                TvAccountPhase.Loading -> IdentityCard(
                    name = state.cachedName.ifBlank { "Cargando…" },
                    rows = emptyList(),
                    loading = true,
                )

                is TvAccountPhase.Error -> IdentityCard(
                    name = state.cachedName.ifBlank { "Sesión" },
                    rows = listOf("Estado" to phase.message),
                    loading = false,
                )

                is TvAccountPhase.Loaded -> IdentityCard(
                    name = phase.profile.fullName.ifBlank { state.cachedName.ifBlank { "Titular" } },
                    rows = profileRows(phase.profile),
                    loading = false,
                )
            }

            Spacer(Modifier.height(36.dp))

            if (!confirming) {
                TvAccountButton(
                    label = "Cerrar sesión",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    focusRequester = logoutFocus,
                    danger = true,
                    onClick = { confirming = true },
                )
            } else {
                Text(
                    text = "¿Seguro que quieres cerrar sesión en esta TV?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // El foco por defecto cae en "Cancelar" (opción segura).
                    TvAccountButton(
                        label = "Cancelar",
                        icon = null,
                        focusRequester = logoutFocus,
                        danger = false,
                        enabled = !state.loggingOut,
                        onClick = { confirming = false },
                    )
                    TvAccountButton(
                        label = if (state.loggingOut) "Cerrando…" else "Sí, salir",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        focusRequester = null,
                        danger = true,
                        enabled = !state.loggingOut,
                        onClick = { viewModel.logout(onLoggedOut) },
                    )
                }
            }
        }
    }
}

private fun profileRows(p: UserProfile): List<Pair<String, String>> = buildList {
    p.idNumber?.takeIf { it.isNotBlank() }?.let { add("Cédula" to it) }
    p.contractNumber?.takeIf { it.isNotBlank() }?.let { add("Contrato" to it) }
    p.email.takeIf { it.isNotBlank() }?.let { add("Correo" to it) }
    p.serviceStatus?.takeIf { it.isNotBlank() }?.let { add("Estado" to it) }
}

@Composable
private fun IdentityCard(
    name: String,
    rows: List<Pair<String, String>>,
    loading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LukiPalette.PanelSurface)
            .border(1.dp, LukiPalette.PanelBorder, RoundedCornerShape(18.dp))
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(LukiPalette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = LukiPalette.OnAccent,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            rows.forEach { (label, value) ->
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("$label: ", color = LukiPalette.PanelMuted, fontSize = 16.sp)
                    Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(color = LukiPalette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TvAccountButton(
    label: String,
    icon: ImageVector?,
    focusRequester: FocusRequester?,
    danger: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "accountBtnScale")

    val bg = when {
        danger && focused -> LukiPalette.LiveRed
        danger            -> LukiPalette.LiveRed.copy(alpha = 0.22f)
        focused           -> LukiPalette.Accent
        else              -> LukiPalette.PanelNeutralBg
    }
    val fg = when {
        danger && focused -> Color.White
        danger            -> LukiPalette.LiveRed
        focused           -> LukiPalette.OnAccent
        else              -> Color.White
    }

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg.copy(alpha = if (enabled) bg.alpha else 0.4f))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Text(text = label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}
