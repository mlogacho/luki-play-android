// ui/CatalogDesign.kt
package com.luki.play.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.luki.play.data.catalog.domain.Channel

/**
 * Piezas visuales compartidas por las pantallas de catálogo (Inicio, Buscar,
 * Mi Lista), traducidas de `frontend`, `app/(app)/(tabs)/home.tsx`.
 *
 * Viven aquí y no dentro de una pantalla porque el portal usa la MISMA card
 * y la misma cabecera en todas sus superficies: duplicarlas garantizaba que
 * se separasen con el primer retoque.
 */
object LukiPalette {
    val GradientTop = Color(0xFF1E0B45)
    val GradientMid = Color(0xFF0D0520)
    val GradientBottom = Color(0xFF05020C)

    val Header = Color(0xFF1E0B45)
    val HeaderBorder = Color(0x0FFFFFFF)      // rgba(255,255,255,0.06)

    val Accent = Color(0xFFFFC107)
    val OnAccent = Color(0xFF140026)

    val CardStrip = Color(0xFF12012A)
    val CardSubtitle = Color(0x6BFFFFFF)      // rgba(255,255,255,0.42)

    val LiveRed = Color(0xFFE53935)

    val PanelSurface = Color(0xFF1A0D30)
    val PanelBorder = Color(0x1AFFFFFF)
    val PanelScrim = Color(0xD105020C)        // rgba(5,2,12,0.82)
    val PanelMuted = Color(0x80FFFFFF)
    val PanelNeutralBg = Color(0x14FFFFFF)    // rgba(255,255,255,0.08)
    val PanelCloseBg = Color(0x0DFFFFFF)      // rgba(255,255,255,0.05)
    val PanelCloseText = Color(0xB3FFFFFF)    // rgba(255,255,255,0.7)
}

/** Fondo de marca: degradado vertical con el quiebre al 35 %. */
@Composable
fun LukiGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to LukiPalette.GradientTop,
                        0.35f to LukiPalette.GradientMid,
                        1f to LukiPalette.GradientBottom,
                    )
                )
            ),
        content = content,
    )
}

/** Ancho de card en móvil: 32 % del ancho de pantalla (`getMobileCardWidth`). */
@Composable
fun rememberChannelCardWidth(): Dp =
    (LocalConfiguration.current.screenWidthDp * 0.32f).dp

/** Separación entre cards (`MOBILE_CHANNEL_GAP`). */
val ChannelCardGap = 8.dp

/** Padding lateral de las filas, según ancho de pantalla. */
@Composable
fun rememberRowPadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp < 420) 16.dp else 20.dp

/** Barra ámbar + icono + título (`SectionHeader` del portal). */
@Composable
fun LukiSectionHeader(
    title: String,
    horizontalPadding: Dp,
    icon: ImageVector = Icons.Outlined.GridView,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LukiPalette.Accent)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LukiPalette.Accent,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Card vertical estilo Apple TV: logo sobre blanco en el 70 % superior y
 * franja oscura abajo con el nombre y la categoría.
 *
 * El corazón es SOLO indicador, nunca un botón: el portal lo degradó a
 * distintivo precisamente porque un icono diminuto dentro de la card era
 * imposible de enfocar con el mando. Marcar y desmarcar se hace desde
 * [ChannelActionPanel].
 */
@Composable
fun LukiChannelCard(
    channel: Channel,
    width: Dp,
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    val height = width * 1.38f
    val thumbHeight = height * 0.70f

    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logoUrl != null) {
                // Medidas del portal: el logo se dimensiona explícitamente
                // (ancho − 20, alto − 24), no por el padding del contenedor.
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(
                        width = width - 20.dp,
                        height = thumbHeight - 24.dp,
                    ),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Tv,
                    contentDescription = null,
                    tint = Color(0x26000000),
                    modifier = Modifier.size(width * 0.45f),
                )
            }

            // Distintivo EN VIVO: todo el catálogo del home es señal en directo.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LukiPalette.LiveRed)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = "EN VIVO",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x73000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorito",
                        tint = LukiPalette.LiveRed,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LukiPalette.CardStrip)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.category.ifBlank { "En vivo" },
                color = LukiPalette.CardSubtitle,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Panel de acciones del canal (Ver ahora / Favorito / Cerrar).
 *
 * Es la interacción del portal: pulsar una card NO lanza el canal, abre este
 * panel. Es además el único sitio donde se marca un favorito.
 */
@Composable
fun ChannelActionPanel(
    channel: Channel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    // Ancho capado por viewport: ~82 % en móvil con tope de 360.
    val panelWidth = minOf((screenWidth * 0.82f).toInt(), 360).dp

    // Ventana propia: en el portal esto es un <Modal>, que tapa TODO. Dibujado
    // como una caja más dentro de la pantalla, el velo se quedaba por debajo de
    // la barra de pestañas y el panel parecía recortado.
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LukiPalette.PanelScrim)
                .clickable(onClick = onClose)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = panelWidth)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LukiPalette.PanelSurface)
                    // Absorbe el toque: pulsar dentro del panel no debe cerrarlo.
                    .clickable(onClick = {})
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (channel.logoUrl != null) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Tv,
                                contentDescription = null,
                                tint = Color(0x33000000),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.category.ifBlank { "En vivo" },
                            color = LukiPalette.PanelMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                PanelAction(
                    icon = Icons.Filled.PlayArrow,
                    label = "Ver ahora",
                    contentColor = LukiPalette.OnAccent,
                    background = LukiPalette.Accent,
                    onClick = onPlay,
                )
                PanelAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    contentColor = if (isFavorite) LukiPalette.LiveRed else Color.White,
                    background = LukiPalette.PanelNeutralBg,
                    onClick = onToggleFavorite,
                )
                PanelAction(
                    icon = Icons.Filled.Close,
                    label = "Cerrar",
                    contentColor = LukiPalette.PanelCloseText,
                    background = LukiPalette.PanelCloseBg,
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
