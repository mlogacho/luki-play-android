// feature/home/HomeScreen.kt
package com.luki.play.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luki.play.R
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.Slider
import com.luki.play.data.catalog.domain.SliderAction
import com.luki.play.ui.ChannelActionPanel
import com.luki.play.ui.ChannelCardGap
import com.luki.play.ui.LukiChannelCard
import com.luki.play.ui.LukiGradientBackground
import com.luki.play.ui.LukiPalette
import com.luki.play.ui.LukiSectionHeader
import com.luki.play.ui.rememberChannelCardWidth
import com.luki.play.ui.rememberRowPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla principal del móvil, réplica del home del portal
 * (`frontend` → `app/(app)/(tabs)/home.tsx`).
 *
 * Layout:
 *   ┌─ Navbar fijo (logo + enlaces de sección + avatar con menú)
 *   ├─ HeroSlider a sangre, con la proporción real del arte
 *   └─ Secciones: cabecera con barra ámbar + fila de cards
 *
 * Qué se replica y qué no: el header sigue al portal en web (enlaces de
 * sección + avatar), que es la versión que hoy ve el usuario dentro del
 * WebView. La barra de pestañas, en cambio, SÍ se recupera (ver
 * [com.luki.play.ui.LukiBottomBar]): el portal la esconde en web y eso
 * dejaba Buscar y Mi Lista sin ninguna vía de acceso.
 */
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(TOP_SECTION_ID) }
    // Se guarda el id, no el objeto: así el panel lee el estado de favorito
    // ACTUAL y el corazón se actualiza en vivo sin cerrarlo.
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    // Índice del primer item de contenido: el hero ocupa el 0 cuando existe.
    val firstSectionIndex = if (state.sliders.isNotEmpty()) 1 else 0
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // El portal aprieta la separación entre secciones en pantallas estrechas.
    val sectionSpacing = if (LocalConfiguration.current.screenWidthDp < 420) 30.dp else 40.dp

    LukiGradientBackground {
        Column(Modifier.fillMaxSize()) {
            LukiNavbar(
                user = state.user,
                sections = state.rows.map { it.category },
                activeSection = activeSection,
                menuOpen = menuOpen,
                onToggleMenu = { menuOpen = !menuOpen },
                onSectionClick = { id ->
                    activeSection = id
                    scope.launch {
                        if (id == TOP_SECTION_ID) {
                            listState.animateScrollToItem(0)
                        } else {
                            val idx = state.rows.indexOfFirst { it.category == id }
                            if (idx >= 0) listState.animateScrollToItem(firstSectionIndex + idx)
                        }
                    }
                },
            )

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Contenedor de secciones del portal: abre con 20, cierra con 60.
                    contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                ) {
                    if (state.sliders.isNotEmpty()) {
                        item(key = "hero") {
                            HeroSlider(
                                sliders = state.sliders,
                                // El slider solo trae el id del canal; el objeto
                                // completo se resuelve contra el catálogo ya cargado.
                                onChannelIdClick = { id ->
                                    state.rows
                                        .asSequence()
                                        .flatMap { it.channels.asSequence() }
                                        .firstOrNull { it.id == id }
                                        ?.let(onChannelClick)
                                },
                            )
                        }
                    }
                    items(state.rows, key = { it.category }) { row ->
                        ChannelSection(
                            row = row,
                            favorites = state.favorites,
                            // Pulsar una card abre el panel de acciones, no
                            // lanza el canal: es la interacción del portal y el
                            // único sitio donde se marca un favorito.
                            onChannelSelected = { selectedChannelId = it.id },
                        )
                    }
                }

                if (state.isRefreshing && state.rows.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = HomePalette.Accent,
                    )
                }
            }
        }

        // ── Menú de cuenta ────────────────────────────────────────────────────
        // Se dibuja sobre el contenido, anclado justo debajo del header.
        val user = state.user
        if (menuOpen && user != null) {
            // Ventana propia en vez de una caja apilada: así el menú gana el
            // hit-test sin competir con el contenido y el cierre por toque
            // fuera o por botón atrás lo gestiona el propio Popup.
            val density = LocalDensity.current
            val topOffsetPx = with(density) {
                (statusBarTop + HEADER_HEIGHT + 6.dp).roundToPx()
            }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, topOffsetPx),
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                AccountMenu(
                    user = user,
                    onLogout = {
                        menuOpen = false
                        confirmLogout = true
                    },
                )
            }
        }

        // Panel de acciones del canal seleccionado. Se resuelve por id contra
        // el catálogo vivo para que el corazón refleje el estado del momento.
        val selected = selectedChannelId?.let { id ->
            state.rows.asSequence().flatMap { it.channels.asSequence() }
                .firstOrNull { it.id == id }
        }
        if (selected != null) {
            ChannelActionPanel(
                channel = selected,
                isFavorite = selected.id in state.favorites,
                onPlay = {
                    selectedChannelId = null
                    onChannelClick(selected)
                },
                onToggleFavorite = {
                    viewModel.toggleFavorite(selected.id, selected.id !in state.favorites)
                },
                onClose = { selectedChannelId = null },
            )
        }

        if (confirmLogout) {
            LogoutConfirmDialog(
                onConfirm = {
                    confirmLogout = false
                    onLogout()
                },
                onCancel = { confirmLogout = false },
            )
        }
    }
}

// ─── Paleta y métricas ────────────────────────────────────────────────────────

/** Colores propios del home: navbar, menu de cuenta y dialogo de logout. */
private object HomePalette {
    val Header = LukiPalette.Header
    val HeaderBorder = LukiPalette.HeaderBorder
    val Accent = LukiPalette.Accent
    val OnAccent = LukiPalette.OnAccent

    val NavInactive = Color(0x99FFFFFF)      // rgba(255,255,255,0.6)

    val MenuSurface = Color(0xFF1A0D30)
    val MenuBorder = Color(0x17FFFFFF)       // rgba(255,255,255,0.09)
    val MenuDivider = Color(0x12FFFFFF)      // rgba(255,255,255,0.07)
    val MenuEmail = Color(0x66FFFFFF)        // rgba(255,255,255,0.4)
    val MenuLogout = Color(0xFFFF453A)
    val MenuLogoutBg = Color(0x2EFF453A)     // rgba(255,69,58,0.18)

    val DialogSurface = Color(0xFF12082A)
    val DialogBody = Color(0x80FFFFFF)       // rgba(255,255,255,0.5)
    val DialogDanger = Color(0xFFF87171)
    val DialogDangerBg = Color(0x26F87171)   // rgba(248,113,113,0.15)
    val DialogCancelText = Color(0xB3FFFFFF) // rgba(255,255,255,0.7)
    val DialogCancelBorder = Color(0x26FFFFFF)

    val Scrim = Color(0xBF000000)            // rgba(0,0,0,0.75)

    /** Colores de plan del portal; el ambar es tambien el valor por defecto. */
    fun planColor(plan: String): Color = when (plan.lowercase()) {
        "lukiplay" -> Color(0xFFFFC107)
        "lukiplay go" -> Color(0xFF00E5FF)
        "basic" -> Color(0xFF60A5FA)
        "premium" -> Color(0xFFA78BFA)
        "pro" -> Color(0xFF34D399)
        "familiar" -> Color(0xFFF472B6)
        "empresarial" -> Color(0xFFFB923C)
        else -> Color(0xFFFFC107)
    }
}

/** `HEADER_H` del portal: 43dp tras los dos recortes documentados allí. */
private val HEADER_HEIGHT = 43.dp
private const val TOP_SECTION_ID = "__top__"

/** Inicial del avatar: nombre, si no correo, si no "U" (`getInitial`). */
private fun initialOf(name: String, email: String): String =
    (name.trim().ifBlank { email.trim() }.ifBlank { "U" }).take(1).uppercase()

// ─── Navbar ───────────────────────────────────────────────────────────────────

/**
 * Barra superior del portal: 43dp de alto sobre #1E0B45, logo horizontal a la
 * izquierda, enlaces de sección desplazables y avatar del usuario a la derecha.
 *
 * Respeta el inset de la barra de estado — sin eso el logo queda debajo del
 * reloj y de los iconos del sistema.
 */
@Composable
private fun LukiNavbar(
    user: HomeUser?,
    sections: List<String>,
    activeSection: String,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onSectionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomePalette.Header)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.luki_logo_h),
                contentDescription = "Luki Play",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(72.dp)
                    .height(20.dp),
            )

            // Enlaces de sección: "Inicio" siempre primero, luego las filas.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavLink(
                    label = "Inicio",
                    selected = activeSection == TOP_SECTION_ID,
                    onClick = { onSectionClick(TOP_SECTION_ID) },
                )
                sections.forEach { section ->
                    NavLink(
                        label = section,
                        selected = activeSection == section,
                        onClick = { onSectionClick(section) },
                    )
                }
            }

            if (user != null) {
                AvatarButton(user = user, open = menuOpen, onClick = onToggleMenu)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HomePalette.HeaderBorder)
        )
    }
}

/**
 * Enlace de sección. El activo va en blanco y negrita con un subrayado ámbar
 * al 60 % del ancho; el resto en blanco al 60 % de opacidad.
 */
@Composable
private fun NavLink(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else HomePalette.NavInactive,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HomePalette.Accent)
            )
        }
    }
}

/** Avatar circular con la inicial sobre el color del plan, más el chevron. */
@Composable
private fun AvatarButton(user: HomeUser, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(HomePalette.planColor(user.plan)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialOf(user.name, user.email),
                color = HomePalette.OnAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Icon(
            imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = "Menú de usuario",
            tint = Color(0x80FFFFFF),
            modifier = Modifier.size(12.dp),
        )
    }
}

// ─── Menú de cuenta ───────────────────────────────────────────────────────────

/**
 * Panel que cuelga del avatar: tarjeta del usuario + cerrar sesión.
 *
 * Divergencia consciente y acotada: el portal lista aquí "Mi Perfil",
 * "Mi Suscripción" y "Mis Dispositivos". Esas tres pantallas todavía no
 * existen en nativo, y una fila que no lleva a ningún sitio es peor que su
 * ausencia — se añaden cuando exista su destino.
 */
@Composable
private fun AccountMenu(
    user: HomeUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val planColor = HomePalette.planColor(user.plan)

    Column(
        modifier = modifier
            .width(256.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HomePalette.MenuSurface)
            .border(1.dp, HomePalette.MenuBorder, RoundedCornerShape(16.dp))
    ) {
        // Tarjeta del usuario, teñida con el color del plan al 8 %.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(planColor.copy(alpha = 0.08f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(planColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialOf(user.name, user.email),
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name.ifBlank { "Mi cuenta" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.email.isNotBlank()) {
                    Text(
                        text = user.email,
                        color = HomePalette.MenuEmail,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HomePalette.MenuDivider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HomePalette.MenuLogoutBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = HomePalette.MenuLogout,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = "Cerrar sesión",
                color = HomePalette.MenuLogout,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Confirmación de cierre de sesión, calcada de `LogoutConfirmModal`. */
@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    // Ventana propia por el mismo motivo que el panel de acciones: si no, el
    // velo no llega a cubrir la barra de pestanas.
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomePalette.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(HomePalette.DialogSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* absorbe el toque: no debe cerrar el diálogo */ }
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HomePalette.DialogDangerBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = HomePalette.DialogDanger,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Text(
                    text = "¿Cerrar sesión?",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Tendrás que volver a ingresar con tus credenciales para acceder al contenido.",
                    color = HomePalette.DialogBody,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HomePalette.DialogCancelBorder)
                            .clickable(onClick = onCancel)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancelar",
                            color = HomePalette.DialogCancelText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HomePalette.DialogDanger)
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cerrar sesión",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

/**
 * Carrusel a sangre. El contenedor ADOPTA la proporción real del arte (medida
 * al cargar la primera imagen), así el banner llena de borde a borde sin
 * franjas moradas ni recorte — misma decisión que el portal.
 *
 * Sin título ni botón superpuestos: el arte del banner ya trae su propio CTA
 * pintado, y encima de él un botón nativo saldría duplicado.
 */
@Composable
private fun HeroSlider(sliders: List<Slider>, onChannelIdClick: (String) -> Unit) {
    // Coalesce duplicados por id; el backend ocasionalmente devuelve repetidos.
    val items = remember(sliders) { sliders.distinctBy { it.id } }
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    var ratio by remember { mutableFloatStateOf(DEFAULT_BANNER_RATIO) }

    // Autoplay: 5,5 s por slide, como AUTOPLAY_MS del portal.
    LaunchedEffect(items.size, pagerState.settledPage) {
        if (items.size <= 1) return@LaunchedEffect
        delay(HERO_AUTOPLAY_MS)
        pagerState.animateScrollToPage((pagerState.settledPage + 1) % items.size)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            // Fondo morado de marca: rellena con color lo que la imagen no cubra.
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A0C6E), Color(0xFF240046), Color(0xFF0D0520))
                )
            )
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val slider = items[page]
            val action = slider.action
            AsyncImage(
                model = slider.imageUrl,
                contentDescription = slider.title,
                contentScale = ContentScale.Crop,
                onSuccess = { result ->
                    if (page == 0) {
                        val size = result.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (action is SliderAction.OpenChannel) {
                            Modifier.clickable { onChannelIdClick(action.channelId) }
                        } else Modifier
                    ),
            )
        }

        // Degradado inferior: funde el arte con el fondo de la página.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color(0x8C05020C),
                            1f to Color(0xEB05020C),
                        )
                    )
                )
        )

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, _ ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (selected) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White else Color(0x59FFFFFF)
                            )
                    )
                }
            }
        }
    }
}

private const val HERO_AUTOPLAY_MS = 5_500L

/** Proporción del arte por defecto hasta que la medición real resuelva. */
private const val DEFAULT_BANNER_RATIO = 1918f / 977f

// ─── Secciones ────────────────────────────────────────────────────────────────

@Composable
private fun ChannelSection(
    row: ChannelRow,
    favorites: Set<String>,
    onChannelSelected: (Channel) -> Unit,
) {
    val horizontalPadding = rememberRowPadding()
    val cardWidth = rememberChannelCardWidth()

    Column {
        LukiSectionHeader(title = row.category, horizontalPadding = horizontalPadding)
        Spacer(Modifier.height(14.dp))

        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(ChannelCardGap),
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            items(row.channels, key = { it.id }) { channel ->
                LukiChannelCard(
                    channel = channel,
                    width = cardWidth,
                    isFavorite = channel.id in favorites,
                    onClick = { onChannelSelected(channel) },
                )
            }
        }
    }
}
