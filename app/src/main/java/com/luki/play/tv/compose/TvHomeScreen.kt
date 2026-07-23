// tv/compose/TvHomeScreen.kt
package com.luki.play.tv.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luki.play.R
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.Slider
import com.luki.play.data.catalog.domain.SliderAction
import com.luki.play.feature.home.ChannelRow
import com.luki.play.feature.home.HomeViewModel
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette

/**
 * Home en Compose for TV, con la línea gráfica de marca (misma paleta del home
 * móvil: fondo morado, cards con logo sobre blanco y distintivo EN VIVO). El
 * esqueleto anterior heredaba el tema claro de tv-material3 (fondo blanco); aquí
 * los colores son explícitos de [LukiPalette].
 *
 * Navegación D-Pad: cada card es `clickable` (⇒ focusable); el foco escala la
 * card y la resalta con borde ámbar. El primer elemento pide foco al entrar.
 */
@Composable
fun TvHomeScreen(
    onChannelClick: (Channel) -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    LukiGradientBackground {
        if (state.isRefreshing && state.rows.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = LukiPalette.Accent,
            )
            return@LukiGradientBackground
        }

        val hero = state.sliders.firstOrNull { it.action is SliderAction.OpenChannel }
            ?: state.sliders.firstOrNull()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item { TvHeader(onOpenAccount = onOpenAccount) }

            if (hero != null) {
                item {
                    TvHero(
                        slider = hero,
                        focusRequester = firstFocus,
                        onClick = {
                            val action = hero.action
                            if (action is SliderAction.OpenChannel) {
                                channelById(state.rows, action.channelId)?.let(onChannelClick)
                            }
                        },
                    )
                }
            }

            itemsIndexed(state.rows) { index, row ->
                TvCategoryRow(
                    row = row,
                    favorites = state.favorites,
                    // Si no hay hero, el foco inicial va a la primera card de la
                    // primera fila para que el mando funcione al entrar.
                    firstCardFocus = if (hero == null && index == 0) firstFocus else null,
                    onChannelClick = onChannelClick,
                )
            }
        }
    }

    LaunchedEffect(state.rows.isNotEmpty(), state.sliders.isNotEmpty()) {
        if (state.rows.isNotEmpty() || state.sliders.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }
}

private fun channelById(rows: List<ChannelRow>, id: String): Channel? =
    rows.asSequence().flatMap { it.channels.asSequence() }.firstOrNull { it.id == id }

// ─── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun TvHeader(onOpenAccount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OVERSCAN, end = OVERSCAN, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.luki_logo_h),
            contentDescription = "Luki Play",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.weight(1f))
        TvAccountChip(onClick = onOpenAccount)
    }
}

/** Acceso a la cuenta (cierre de sesión) desde el home. Enfocable con el mando. */
@Composable
private fun TvAccountChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "accountChipScale")

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) LukiPalette.Accent else Color(0x1FFFFFFF))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = "Mi cuenta",
            tint = if (focused) LukiPalette.OnAccent else Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Mi cuenta",
            color = if (focused) LukiPalette.OnAccent else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun TvHero(slider: Slider, focusRequester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "heroScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OVERSCAN)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .aspectRatio(16f / 6f)
            .clip(RoundedCornerShape(16.dp))
            .background(LukiPalette.GradientMid)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) LukiPalette.Accent else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = slider.imageUrl,
            contentDescription = slider.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Degradado inferior para fundir el arte con el fondo.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, LukiPalette.GradientBottom.copy(alpha = 0.85f)))
                )
        )
    }
}

// ─── Fila de categoría ──────────────────────────────────────────────────────────

@Composable
private fun TvCategoryRow(
    row: ChannelRow,
    favorites: Set<String>,
    firstCardFocus: FocusRequester?,
    onChannelClick: (Channel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TvSectionHeader(title = row.category)
        LazyRow(
            contentPadding = PaddingValues(horizontal = OVERSCAN),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(row.channels) { index, channel ->
                TvChannelCard(
                    channel = channel,
                    isFavorite = channel.id in favorites,
                    focusRequester = if (index == 0) firstCardFocus else null,
                    onClick = { onChannelClick(channel) },
                )
            }
        }
    }
}

@Composable
private fun TvSectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(start = OVERSCAN),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LukiPalette.Accent)
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Card ───────────────────────────────────────────────────────────────────────

@Composable
private fun TvChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")

    val cardWidth = 200.dp
    val thumbHeight = 132.dp

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) LukiPalette.Accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(thumbHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = cardWidth - 28.dp, height = thumbHeight - 28.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Tv,
                    contentDescription = null,
                    tint = Color(0x26000000),
                    modifier = Modifier.size(cardWidth * 0.4f),
                )
            }

            // Distintivo EN VIVO: todo el catálogo del home es señal en directo.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LukiPalette.LiveRed)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                Text("EN VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x73000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Favorito", tint = LukiPalette.LiveRed, modifier = Modifier.size(16.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LukiPalette.CardStrip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.category.ifBlank { "En vivo" },
                color = LukiPalette.CardSubtitle,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Margen de sobrebarrido (overscan) de 10 pies. */
private val OVERSCAN = 48.dp
