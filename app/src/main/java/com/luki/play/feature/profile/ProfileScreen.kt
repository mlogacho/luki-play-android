// feature/profile/ProfileScreen.kt
package com.luki.play.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.auth.UserProfile
import com.luki.play.feature.login.PasswordPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla de cuenta, réplica de `frontend/app/(app)/profile.tsx`.
 *
 * A diferencia del resto de la app (morado de marca), el portal viste esta
 * pantalla con un estilo iOS-settings (Apple-dark): fondo negro, tarjetas
 * `#1C1C1E`, filas con iconos cuadrados de color y hojas inferiores. Se
 * replica ese estilo tal cual — el criterio es que quede idéntico al portal.
 *
 * @param onBack cierra la pantalla (vuelve a Inicio).
 * @param onLogout cierra sesión — lo maneja el NavGraph para vaciar el back-stack.
 * @param onOpenDevices abre "Mis Dispositivos". Aún sin pantalla nativa: hoy
 *   cae al portal (igual que activación); se reemplaza cuando exista.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenDevices: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showChangePwd by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scrolledDp = with(density) { scrollState.value.toDp().value }
    // Mismas curvas que el portal: el avatar se desvanece [0,80] y el título
    // de la barra aparece [60,110].
    val avatarAlpha = (1f - scrolledDp / 80f).coerceIn(0f, 1f)
    val titleAlpha = ((scrolledDp - 60f) / 50f).coerceIn(0f, 1f)

    val phase = state.phase
    val planColor = ProfilePalette.planColor(state.plan)
    val fullName = (phase as? ProfilePhase.Loaded)?.profile?.fullName?.takeIf { it.isNotBlank() }
        ?: state.cachedName

    Box(Modifier.fillMaxSize().background(ProfilePalette.Bg)) {
        Column(Modifier.fillMaxSize()) {
            ProfileNavBar(
                title = fullName.ifBlank { "Mi Perfil" },
                titleAlpha = titleAlpha,
                onBack = onBack,
            )

            when (phase) {
                is ProfilePhase.Loading -> LoadingState()
                is ProfilePhase.Error -> ErrorState(message = phase.message, onRetry = viewModel::load)
                is ProfilePhase.Loaded -> ProfileContent(
                    profile = phase.profile,
                    fullName = fullName,
                    cachedEmail = state.cachedEmail,
                    planColor = planColor,
                    scrollState = scrollState,
                    avatarAlpha = avatarAlpha,
                    onChangePassword = { showChangePwd = true },
                    onOpenDevices = onOpenDevices,
                    onLogout = { showLogout = true },
                )
            }
        }
    }

    if (showChangePwd) {
        ChangePasswordSheet(
            onDismiss = { showChangePwd = false },
            onSubmit = { current, next, onSuccess, onError ->
                viewModel.changePassword(current, next, onSuccess, onError)
            },
            onChanged = {
                // El backend ya revocó todas las sesiones: hay que salir.
                showChangePwd = false
                onLogout()
            },
        )
    }
    if (showLogout) {
        LogoutActionSheet(
            onConfirm = {
                showLogout = false
                onLogout()
            },
            onCancel = { showLogout = false },
        )
    }
}

// ─── Paleta (Apple-dark del portal) ─────────────────────────────────────────────

private object ProfilePalette {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val Separator = Color(0xA6545458)       // rgba(84,84,88,0.65)
    val SeparatorInset = Color(0x73545458)  // rgba(84,84,88,0.45)
    val Label = Color(0xFFFFFFFF)
    val LabelSecond = Color(0x99EBEBF5)     // rgba(235,235,245,0.6)
    val LabelThird = Color(0x4DEBEBF5)      // rgba(235,235,245,0.3)
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Purple = Color(0xFFBF5AF2)
    val Yellow = Color(0xFFFFD60A)
    val Teal = Color(0xFF5AC8FA)
    val Pink = Color(0xFFFF375F)
    val InputBorder = Color(0x80545458)     // rgba(84,84,88,0.5)
    val Scrim = Color(0x8C000000)           // rgba(0,0,0,0.55)

    /** `PLAN_COLORS` del portal; el ámbar es también el valor por defecto. */
    fun planColor(plan: String): Color = when (plan.lowercase()) {
        "lukiplay" -> Yellow
        "lukiplay go" -> Color(0xFF00E5FF)
        "basic" -> Blue
        "premium" -> Purple
        "pro" -> Green
        "familiar" -> Pink
        "empresarial" -> Orange
        else -> Yellow
    }

    /** `SERVICE_STATUS_CFG` del portal: etiqueta + color por estado. */
    fun serviceStatus(status: String): Pair<String, Color> = when (status) {
        "ACTIVE" -> "Activo" to Green
        "SUSPENDED" -> "Suspendido" to Orange
        "CANCELLED" -> "Cancelado" to Red
        "PENDING" -> "Pendiente" to LabelSecond
        else -> status to LabelSecond
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────────

/** Iniciales: primeras letras de hasta dos palabras (`initials` del portal). */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "U" }

/**
 * Formatea una fecha ISO como el portal (`formatDateTime` → es-EC, día/mes/año
 * y hora:minuto). Se usa `SimpleDateFormat` en vez de `java.time` porque el
 * minSdk es 23 y no hay desugaring; ante cualquier fallo devuelve "—".
 */
private fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(iso.take(19)) ?: return "—"
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "EC")).format(date)
    }.getOrDefault("—")
}

// ─── Barra de navegación ────────────────────────────────────────────────────────

@Composable
private fun ProfileNavBar(title: String, titleAlpha: Float, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ProfilePalette.Bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = null,
                    tint = ProfilePalette.Blue,
                    modifier = Modifier.size(24.dp),
                )
                Text("Inicio", color = ProfilePalette.Blue, fontSize = 17.sp)
            }

            Text(
                text = title,
                color = ProfilePalette.Label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 80.dp)
                    .alpha(titleAlpha),
            )
        }
        HairLine(ProfilePalette.Separator)
    }
}

// ─── Estados de carga / error ───────────────────────────────────────────────────

@Composable
private fun ColumnScope.LoadingState() {
    Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ProfilePalette.Blue)
    }
}

@Composable
private fun ColumnScope.ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().weight(1f).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = ProfilePalette.LabelThird,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = message,
            color = ProfilePalette.LabelSecond,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(ProfilePalette.Blue)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 11.dp),
        ) {
            Text("Reintentar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Contenido ──────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.ProfileContent(
    profile: UserProfile,
    fullName: String,
    cachedEmail: String,
    planColor: Color,
    scrollState: androidx.compose.foundation.ScrollState,
    avatarAlpha: Float,
    onChangePassword: () -> Unit,
    onOpenDevices: () -> Unit,
    onLogout: () -> Unit,
) {
    val email = profile.email.takeIf { it.isNotBlank() } ?: cachedEmail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .verticalScroll(scrollState),
    ) {
        AvatarHero(
            fullName = fullName,
            email = email,
            planColor = planColor,
            canAccessOtt = profile.canAccessOtt,
            alpha = avatarAlpha,
        )

        // ── Información personal ──
        ProfileSection(label = "Información personal") {
            ProfileRow(Icons.Filled.Person, ProfilePalette.Blue, "Nombre", value = fullName.ifBlank { "—" })
            ProfileRow(Icons.Filled.AlternateEmail, ProfilePalette.Blue, "Email", value = email.ifBlank { "—" })
            ProfileRow(Icons.Filled.CreditCard, ProfilePalette.Green, "Cédula", value = profile.idNumber ?: "—", isLast = true)
        }

        // ── Contrato y servicio ──
        if (!profile.contractNumber.isNullOrBlank() || !profile.serviceStatus.isNullOrBlank()) {
            ProfileSection(label = "Contrato y servicio") {
                val hasStatus = !profile.serviceStatus.isNullOrBlank()
                if (!profile.contractNumber.isNullOrBlank()) {
                    ProfileRow(
                        Icons.Filled.Description, ProfilePalette.Orange,
                        "N° Contrato", value = profile.contractNumber,
                        isLast = !hasStatus,
                    )
                }
                if (hasStatus) {
                    val (label, color) = ProfilePalette.serviceStatus(profile.serviceStatus!!)
                    ProfileRow(
                        Icons.Filled.CheckCircle, color,
                        "Estado", value = label, valueColor = color, isLast = true,
                    )
                }
            }
        }

        // ── Sesión ──
        if (!profile.lastLoginAt.isNullOrBlank()) {
            ProfileSection(label = "Sesión") {
                ProfileRow(
                    Icons.Filled.Schedule, ProfilePalette.SurfaceHigh,
                    "Último acceso", value = formatDateTime(profile.lastLoginAt), isLast = true,
                )
            }
        }

        // ── Cuenta ──
        ProfileSection(
            label = "Cuenta",
            footer = "Al cambiar tu contraseña, todas tus sesiones activas serán cerradas.",
        ) {
            ProfileRow(
                Icons.Filled.PhoneAndroid, ProfilePalette.Teal,
                "Mis Dispositivos", chevron = true, onClick = onOpenDevices,
            )
            ProfileRow(
                Icons.Filled.Lock, ProfilePalette.Purple,
                "Cambiar contraseña", chevron = true, isLast = true, onClick = onChangePassword,
            )
        }

        // ── Cerrar sesión ──
        ProfileSection {
            ProfileRow(
                Icons.AutoMirrored.Filled.Logout, ProfilePalette.Red,
                "Cerrar sesión", isLast = true, destructive = true, onClick = onLogout,
            )
        }

        Text(
            text = "Luki Play · " + (profile.id.takeIf { it.isNotBlank() }?.let { "ID ${it.take(8).uppercase()}" } ?: ""),
            color = ProfilePalette.LabelThird,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 32.dp, end = 32.dp),
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun AvatarHero(
    fullName: String,
    email: String,
    planColor: Color,
    canAccessOtt: Boolean,
    alpha: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(top = 28.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(planColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(fullName),
                color = Color.Black,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = fullName.ifBlank { "—" },
            color = ProfilePalette.Label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = email, color = ProfilePalette.LabelSecond, fontSize = 15.sp)

        if (!canAccessOtt) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ProfilePalette.Red.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = ProfilePalette.Red,
                    modifier = Modifier.size(13.dp),
                )
                Text("Acceso restringido", color = ProfilePalette.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Sección + fila (estilo lista agrupada iOS) ─────────────────────────────────

@Composable
private fun ProfileSection(
    label: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (label != null) {
            Text(
                text = label,
                color = ProfilePalette.LabelSecond,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ProfilePalette.Surface),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                color = ProfilePalette.LabelThird,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    value: String? = null,
    valueColor: Color? = null,
    isLast: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                color = if (destructive) ProfilePalette.Red else ProfilePalette.Label,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = value,
                    color = valueColor ?: ProfilePalette.LabelSecond,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            if (chevron) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = ProfilePalette.LabelThird,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (!isLast) {
            HairLine(ProfilePalette.SeparatorInset, startInset = 58.dp)
        }
    }
}

/** Separador fino a 0.5dp; opcionalmente sangrado por la izquierda. */
@Composable
private fun HairLine(color: Color, startInset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(0.5.dp)
            .background(color),
    )
}

// ─── Hoja: cambiar contraseña ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(
    onDismiss: () -> Unit,
    onSubmit: (current: String, next: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onChanged: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ProfilePalette.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(ProfilePalette.SurfaceHigh))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            // Cabecera
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Cancelar",
                    color = ProfilePalette.Blue,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
                Text(
                    "Cambiar contraseña",
                    color = ProfilePalette.Label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            HairLine(ProfilePalette.Separator)

            if (done) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(ProfilePalette.Green.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = ProfilePalette.Green, modifier = Modifier.size(28.dp))
                    }
                    Text("Contraseña actualizada", color = ProfilePalette.Label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Todas tus sesiones han sido cerradas. Iniciando sesión nuevamente…",
                        color = ProfilePalette.LabelSecond,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    if (error.isNotBlank()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ProfilePalette.Red.copy(alpha = 0.1f))
                                .border(1.dp, ProfilePalette.Red.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Text(error, color = ProfilePalette.Red, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    PasswordField("Contraseña actual", current, { current = it })
                    PasswordField("Nueva contraseña", next, { next = it }, placeholder = PasswordPolicy.RULE_HINT)
                    PasswordField("Confirmar nueva contraseña", confirm, { confirm = it }, placeholder = "Repite la nueva contraseña", imeAction = ImeAction.Done)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProfilePalette.Blue.copy(alpha = if (loading) 0.6f else 1f))
                            .clickable(enabled = !loading) {
                                val pErr = PasswordPolicy.validate(next)
                                val validation = when {
                                    current.isBlank() -> "Ingresa tu contraseña actual"
                                    pErr != null -> pErr
                                    next != confirm -> "Las contraseñas no coinciden"
                                    else -> null
                                }
                                if (validation != null) {
                                    error = validation
                                } else {
                                    error = ""
                                    loading = true
                                    onSubmit(
                                        current, next,
                                        {
                                            loading = false
                                            done = true
                                            scope.launch { delay(1800); onChanged() }
                                        },
                                        { msg -> loading = false; error = msg },
                                    )
                                }
                            }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Actualizar contraseña", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        "Al cambiar tu contraseña, todas tus sesiones activas serán cerradas.",
                        color = ProfilePalette.LabelThird,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "••••••••",
    imeAction: ImeAction = ImeAction.Next,
) {
    var show by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, color = ProfilePalette.LabelSecond, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ProfilePalette.Surface)
                .border(1.dp, if (focused) ProfilePalette.Blue else ProfilePalette.InputBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 13.dp)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = ProfilePalette.Label, fontSize = 16.sp),
                cursorBrush = SolidColor(ProfilePalette.Blue),
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
                keyboardActions = KeyboardActions(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = ProfilePalette.LabelThird, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
            Icon(
                imageVector = if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                tint = ProfilePalette.LabelThird,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { show = !show },
            )
        }
    }
}

// ─── Hoja de acción: cerrar sesión (estilo action-sheet iOS) ────────────────────

@Composable
private fun LogoutActionSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProfilePalette.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Grupo de acción
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ProfilePalette.Surface),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Cerrar sesión", color = ProfilePalette.Label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Se cerrará la sesión en este dispositivo.",
                            color = ProfilePalette.LabelSecond,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    HairLine(ProfilePalette.Separator)
                    Box(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onConfirm).padding(vertical = 17.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cerrar sesión", color = ProfilePalette.Red, fontSize = 20.sp)
                    }
                }
                // Botón Cancelar en pastilla aparte (estilo iOS)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ProfilePalette.Surface)
                        .clickable(onClick = onCancel)
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", color = ProfilePalette.Blue, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
