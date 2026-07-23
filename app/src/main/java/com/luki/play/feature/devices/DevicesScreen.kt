// feature/devices/DevicesScreen.kt
package com.luki.play.feature.devices

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.devices.Device
import com.luki.play.data.devices.DeviceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

/**
 * "Mis Dispositivos", réplica de `frontend/app/(app)/devices.tsx`.
 * Mismo estilo Apple-dark que el perfil (el propio portal lo comenta así).
 */
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<Device?>(null) }
    var removeTarget by remember { mutableStateOf<Device?>(null) }

    Column(Modifier.fillMaxSize().background(DevPalette.Bg)) {
        DevNavBar(onBack = onBack)

        when (val phase = state.phase) {
            is DevicesPhase.Loading -> LoadingState()
            is DevicesPhase.Error -> ErrorState(message = phase.message, onRetry = viewModel::load)
            is DevicesPhase.Loaded -> DevicesContent(
                devices = phase.devices,
                limit = phase.limit,
                onRename = { renameTarget = it },
                onRemove = { removeTarget = it },
            )
        }
    }

    renameTarget?.let { target ->
        RenameSheet(
            initialName = deviceDisplayName(target),
            onDismiss = { renameTarget = null },
            onSave = { name ->
                viewModel.rename(target.fingerprint, name)
                renameTarget = null
            },
        )
    }

    removeTarget?.let { target ->
        RemoveConfirmDialog(
            deviceName = deviceDisplayName(target),
            onConfirm = {
                viewModel.remove(target.fingerprint)
                removeTarget = null
            },
            onCancel = { removeTarget = null },
        )
    }

    actionError?.let { message ->
        AlertDialogPopup(message = message, onOk = viewModel::consumeActionError)
    }
}

// ─── Barra de navegación ────────────────────────────────────────────────────────

@Composable
private fun DevNavBar(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(DevPalette.Bg)) {
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
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = DevPalette.Blue, modifier = Modifier.size(24.dp))
                Text("Perfil", color = DevPalette.Blue, fontSize = 17.sp)
            }
            Text(
                "Mis Dispositivos",
                color = DevPalette.Label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        HairLine(DevPalette.Separator)
    }
}

// ─── Estados ────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.LoadingState() {
    Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DevPalette.Blue)
    }
}

@Composable
private fun ColumnScope.ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().weight(1f).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = DevPalette.LabelThird, modifier = Modifier.size(52.dp))
        Text(message, color = DevPalette.LabelSecond, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(DevPalette.Blue)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 11.dp),
        ) {
            Text("Reintentar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Contenido ──────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DevicesContent(
    devices: List<Device>,
    limit: Int,
    onRename: (Device) -> Unit,
    onRemove: (Device) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
    ) {
        SlotIndicator(used = devices.size, limit = limit)

        Spacer(Modifier.height(20.dp))

        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = DevPalette.LabelThird, modifier = Modifier.size(48.dp))
                Text("No hay dispositivos registrados", color = DevPalette.LabelSecond, fontSize = 15.sp)
                Text(
                    "Inicia sesión desde un dispositivo para que aparezca aquí.",
                    color = DevPalette.LabelThird, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        } else {
            Text(
                "Dispositivos activos",
                color = DevPalette.LabelSecond, fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DevPalette.Surface),
            ) {
                devices.forEachIndexed { i, device ->
                    DeviceCard(
                        device = device,
                        isLast = i == devices.lastIndex,
                        onRename = { onRename(device) },
                        onRemove = { onRemove(device) },
                    )
                }
            }
        }

        Text(
            "Al eliminar un dispositivo, su sesión activa será cerrada automáticamente.",
            color = DevPalette.LabelThird, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SlotIndicator(used: Int, limit: Int) {
    val color = when {
        used >= limit -> DevPalette.Red
        used >= limit - 1 -> DevPalette.Orange
        else -> DevPalette.Green
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DevPalette.Surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.125f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("$used de $limit dispositivos", color = DevPalette.Label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (used >= limit) "Límite alcanzado. Elimina uno para registrar otro."
                    else "Puedes agregar ${limit - used} ${plural(limit - used, "dispositivo", "dispositivos")} más.",
                    color = DevPalette.LabelSecond, fontSize = 13.sp,
                )
            }
        }
        // Barra de progreso al pie de la tarjeta.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(DevPalette.SurfaceHigh)) {
            val fraction = if (limit > 0) min(used.toFloat() / limit, 1f) else 0f
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(color))
        }
    }
}

@Composable
private fun DeviceCard(device: Device, isLast: Boolean, onRename: () -> Unit, onRemove: () -> Unit) {
    val isCurrent = device.isCurrentDevice
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isCurrent) DevPalette.Blue.copy(alpha = 0.18f) else DevPalette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    deviceIcon(device.tipo),
                    contentDescription = null,
                    tint = if (isCurrent) DevPalette.Blue else DevPalette.LabelSecond,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        deviceDisplayName(device),
                        color = DevPalette.Label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isCurrent) {
                        Box(
                            Modifier.clip(RoundedCornerShape(5.dp)).background(DevPalette.Blue.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("Este", color = DevPalette.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(deviceSubtitle(device), color = DevPalette.LabelSecond, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Visto ${timeAgo(device.lastSeenAt)} · ${device.ipAddress ?: "IP desconocida"}",
                    color = DevPalette.LabelThird, fontSize = 12.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareIconButton(Icons.Filled.Edit, DevPalette.SurfaceHigh, DevPalette.LabelSecond, onRename)
                if (!isCurrent) {
                    SquareIconButton(Icons.Outlined.DeleteOutline, DevPalette.Red.copy(alpha = 0.12f), DevPalette.Red, onRemove)
                }
            }
        }
        if (!isLast) HairLine(DevPalette.SepInset, startInset = 72.dp)
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

// ─── Hoja: renombrar ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DevPalette.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(DevPalette.SurfaceHigh))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Cancelar", color = DevPalette.Blue, fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.CenterStart).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
                    ),
                )
                Text("Renombrar", color = DevPalette.Label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                val enabled = value.isNotBlank()
                Text(
                    "Guardar",
                    color = if (enabled) DevPalette.Blue else DevPalette.LabelThird,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled,
                    ) { onSave(value.trim()) },
                )
            }
            HairLine(DevPalette.Separator)
            Box(Modifier.padding(20.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DevPalette.SurfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = TextStyle(color = DevPalette.Label, fontSize = 17.sp),
                        cursorBrush = SolidColor(DevPalette.Blue),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onSave(value.trim()) }),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text("Nombre del dispositivo", color = DevPalette.LabelThird, fontSize = 17.sp)
                            }
                            inner()
                        },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ─── Diálogos (alerta centrada estilo iOS) ──────────────────────────────────────

@Composable
private fun RemoveConfirmDialog(deviceName: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertScaffold(onDismiss = onCancel) {
        Text("Eliminar dispositivo", color = DevPalette.Label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "¿Eliminar \"$deviceName\"?\n\nSu sesión en este dispositivo será cerrada.",
            color = DevPalette.LabelSecond, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", DevPalette.SurfaceHigh, DevPalette.Label, Modifier.weight(1f), onCancel)
            DialogButton("Eliminar", DevPalette.Red, Color.White, Modifier.weight(1f), onConfirm)
        }
    }
}

@Composable
private fun AlertDialogPopup(message: String, onOk: () -> Unit) {
    AlertScaffold(onDismiss = onOk) {
        Text(message, color = DevPalette.Label, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        DialogButton("OK", DevPalette.Blue, Color.White, Modifier.fillMaxWidth(), onOk)
    }
}

@Composable
private fun AlertScaffold(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x8C000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(DevPalette.Surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* absorbe */ }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun DialogButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun HairLine(color: Color, startInset: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = startInset).height(0.5.dp).background(color))
}

private fun deviceIcon(tipo: DeviceType): ImageVector = when (tipo) {
    DeviceType.MOBILE -> Icons.Outlined.PhoneAndroid
    DeviceType.TABLET -> Icons.Outlined.TabletAndroid
    DeviceType.DESKTOP -> Icons.Outlined.DesktopWindows
    DeviceType.SMART_TV -> Icons.Outlined.Tv
    DeviceType.UNKNOWN -> Icons.Outlined.DeviceUnknown
}

private fun deviceTypeLabel(tipo: DeviceType): String = when (tipo) {
    DeviceType.MOBILE -> "Celular"
    DeviceType.TABLET -> "Tablet"
    DeviceType.DESKTOP -> "Computadora"
    DeviceType.SMART_TV -> "Smart TV"
    DeviceType.UNKNOWN -> "Dispositivo"
}

private fun deviceSubtitle(device: Device): String {
    val parts = listOfNotNull(device.os?.takeIf { it.isNotBlank() }, device.browser?.takeIf { it.isNotBlank() })
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else deviceTypeLabel(device.tipo)
}

private fun deviceDisplayName(device: Device): String =
    device.nombre?.takeIf { it.isNotBlank() } ?: deviceSubtitle(device)

private fun plural(n: Int, singular: String, plural: String): String = if (n == 1) singular else plural

private fun timeAgo(iso: String?): String {
    if (iso.isNullOrBlank()) return "Nunca"
    val date = parseIso(iso) ?: return "Nunca"
    val mins = ((System.currentTimeMillis() - date.time) / 60_000L).toInt()
    return when {
        mins < 2 -> "Ahora mismo"
        mins < 60 -> "Hace $mins min"
        mins < 1440 -> "Hace ${mins / 60}h"
        mins < 10_080 -> "Hace ${mins / 1440}d"
        else -> runCatching { SimpleDateFormat("dd MMM", Locale("es", "EC")).format(date) }.getOrDefault("Nunca")
    }
}

private fun parseIso(iso: String): Date? = runCatching {
    val s = iso.take(19)
    val pattern = if (s.length > 10) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd"
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)
}.getOrNull()

// ─── Paleta (Apple-dark, igual que el perfil) ───────────────────────────────────

private object DevPalette {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val Separator = Color(0xA6545458)
    val SepInset = Color(0x73545458)
    val Label = Color(0xFFFFFFFF)
    val LabelSecond = Color(0x99EBEBF5)
    val LabelThird = Color(0x4DEBEBF5)
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
}
