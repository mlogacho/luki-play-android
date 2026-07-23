// feature/subscription/SubscriptionScreen.kt
package com.luki.play.feature.subscription

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.subscription.MePlan
import com.luki.play.data.subscription.PlanInfo
import com.luki.play.data.subscription.SubscriptionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Pantalla "Mi Suscripción", réplica de `frontend/app/(app)/subscription.tsx`.
 *
 * A diferencia del perfil (estilo iOS), esta sí viste el morado de marca:
 * fondo `#05020C`, tarjetas `#12082A` y acento ámbar `#FFC107`.
 */
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(SubPalette.Bg)) {
        // ── Header ──
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SubPalette.Chip)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text("Mi Suscripción", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SubPalette.HeaderBorder))
        }

        when (val phase = state.phase) {
            is SubscriptionPhase.Loading -> LoadingState()
            is SubscriptionPhase.Error -> ErrorState(message = phase.message, onRetry = viewModel::load)
            is SubscriptionPhase.Loaded -> LoadedContent(mePlan = phase.mePlan, cachedPlan = state.cachedPlan)
        }
    }
}

// ─── Estados ────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.LoadingState() {
    Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SubPalette.Accent)
    }
}

@Composable
private fun ColumnScope.ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().weight(1f).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Color(0x26FFFFFF), modifier = Modifier.size(48.dp))
        Text(message, color = Color(0x80FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SubPalette.Accent.copy(alpha = 0.133f))
                .border(1.dp, SubPalette.Accent.copy(alpha = 0.333f), RoundedCornerShape(10.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text("Reintentar", color = SubPalette.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Contenido ──────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.LoadedContent(mePlan: MePlan, cachedPlan: String) {
    val plan = mePlan.plan ?: return   // el portal no pinta nada sin plan
    val sub = mePlan.subscription
    val planColor = SubPalette.planColor(plan.nombre.ifBlank { cachedPlan })
    val remaining = sub?.let { daysUntil(it.expirationDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PlanHeroCard(plan = plan, sub = sub, planColor = planColor, remaining = remaining)

        if (sub != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("VIGENCIA")
                SectionCard {
                    FeatureRow(Icons.Outlined.CalendarToday, "Fecha de inicio", formatDate(sub.startDate))
                    FeatureRow(
                        Icons.Outlined.EventBusy, "Fecha de vencimiento", formatDate(sub.expirationDate),
                        accent = remaining != null && remaining <= 7,
                    )
                    if (!sub.gracePeriodEnd.isNullOrBlank()) {
                        FeatureRow(Icons.Outlined.HourglassEmpty, "Fin período de gracia", formatDate(sub.gracePeriodEnd))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("CARACTERÍSTICAS DEL PLAN")
            SectionCard {
                FeatureRow(Icons.Outlined.Tv, "Calidad de video", videoQualityLabel(plan.videoQuality), accent = true)
                FeatureRow(Icons.Outlined.PhoneAndroid, "Dispositivos simultáneos", "${plan.maxDevices} ${plural(plan.maxDevices, "dispositivo", "dispositivos")}")
                FeatureRow(Icons.Outlined.PlayCircle, "Streams simultáneos", "${plan.maxConcurrentStreams} ${plural(plan.maxConcurrentStreams, "stream", "streams")}")
                FeatureRow(Icons.Outlined.People, "Perfiles", "${plan.maxProfiles} ${plural(plan.maxProfiles, "perfil", "perfiles")}")
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PlanHeroCard(plan: PlanInfo, sub: SubscriptionInfo?, planColor: Color, remaining: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SubPalette.Card)
            .border(1.dp, planColor.copy(alpha = 0.19f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Nombre + insignia de estado
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(planColor.copy(alpha = 0.125f))
                    .border(1.dp, planColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = planColor, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(plan.nombre.ifBlank { "—" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                if (plan.descripcion.isNotBlank()) {
                    Text(plan.descripcion, color = Color(0x73FFFFFF), fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            val statusCfg = sub?.let { SubPalette.statusCfg(it.status) }
            if (statusCfg != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusCfg.bg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(statusCfg.label, color = statusCfg.color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Precio
        if (plan.precio != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val prefix = if (plan.moneda == "USD") "$" else plan.moneda
                Text("$prefix${String.format(Locale.US, "%.2f", plan.precio)}", color = planColor, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("/ mes", color = Color(0x59FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        // Cuenta regresiva de vencimiento
        if (sub != null && remaining != null) {
            val urgent = remaining <= 7
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (urgent) Color(0x1FF87171) else Color(0x0DFFFFFF))
                    .border(1.dp, if (urgent) Color(0x40F87171) else Color(0x12FFFFFF), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (remaining > 0) "VENCE EN" else "VENCIÓ HACE",
                    color = if (urgent) SubPalette.Danger else Color(0x66FFFFFF),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    "${abs(remaining)} ${plural(abs(remaining), "día", "días")}",
                    color = if (urgent) SubPalette.Danger else Color.White,
                    fontSize = 22.sp, fontWeight = FontWeight.Black,
                )
                Text("Vence el ${formatDate(sub.expirationDate)}", color = Color(0x59FFFFFF), fontSize = 12.sp)
            }
        }
    }
}

// ─── Piezas de sección ──────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0x59FFFFFF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SubPalette.Card)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp),
        content = content,
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, label: String, value: String, accent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (accent) SubPalette.Accent.copy(alpha = 0.125f) else Color(0x0DFFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (accent) SubPalette.Accent else Color(0x80FFFFFF), modifier = Modifier.size(15.dp))
        }
        Text(label, color = Color(0x8CFFFFFF), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (accent) SubPalette.Accent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Paleta + helpers ───────────────────────────────────────────────────────────

private object SubPalette {
    val Bg = Color(0xFF05020C)
    val Card = Color(0xFF12082A)
    val Accent = Color(0xFFFFC107)
    val Danger = Color(0xFFF87171)
    val Chip = Color(0x0FFFFFFF)         // rgba(255,255,255,0.06)
    val HeaderBorder = Color(0x0FFFFFFF)

    fun planColor(nombre: String): Color = when (nombre.lowercase()) {
        "lukiplay" -> Accent
        "lukiplay go" -> Color(0xFF00E5FF)
        "basic" -> Color(0xFF60A5FA)
        "premium" -> Color(0xFFA78BFA)
        "pro" -> Color(0xFF34D399)
        "familiar" -> Color(0xFFF472B6)
        "empresarial" -> Color(0xFFFB923C)
        else -> Accent
    }

    data class StatusCfg(val label: String, val color: Color, val bg: Color)

    fun statusCfg(status: String): StatusCfg? = when (status) {
        "ACTIVE" -> StatusCfg("Activo", Color(0xFF34D399), Color(0x1F34D399))
        "GRACE_PERIOD" -> StatusCfg("Período de gracia", Color(0xFFFCD34D), Color(0x1FFCD34D))
        "SUSPENDED" -> StatusCfg("Suspendido", Color(0xFFFB923C), Color(0x1FFB923C))
        "CANCELLED" -> StatusCfg("Cancelado", Color(0xFFF87171), Color(0x1FF87171))
        else -> null
    }
}

private fun videoQualityLabel(q: String): String = when (q.uppercase()) {
    "SD" -> "Estándar (SD)"
    "HD" -> "Alta definición (HD)"
    "FHD" -> "Full HD (1080p)"
    "UHD", "4K" -> "Ultra HD (4K)"
    else -> q
}

private fun plural(n: Int, singular: String, plural: String): String =
    if (n == 1) singular else plural

/** Parsea una fecha ISO (con o sin hora) a [Date], o null si no puede. */
private fun parseIso(iso: String): Date? = runCatching {
    val s = iso.take(19)
    val pattern = if (s.length > 10) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd"
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)
}.getOrNull()

/** Fecha larga es-EC ("22 de julio de 2026"), o "—" si no parsea. */
private fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val date = parseIso(iso) ?: return "—"
    return SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "EC")).format(date)
}

/** Días hasta la fecha (redondeo hacia arriba, como `daysUntil` del portal). */
private fun daysUntil(iso: String): Int {
    val date = parseIso(iso) ?: return 0
    return ceil((date.time - System.currentTimeMillis()) / 86_400_000.0).toInt()
}
