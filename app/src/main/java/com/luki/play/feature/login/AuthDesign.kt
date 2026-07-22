// feature/login/AuthDesign.kt
package com.luki.play.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import com.luki.play.R

/**
 * Sistema de diseño de las pantallas de autenticación.
 *
 * Réplica 1:1 de la paleta `P` y de los componentes compartidos de
 * `frontend/app/(auth)/login.tsx` del portal (AuthInput, PrimaryButton,
 * ErrorBox, BackLink y el shell con gradiente + logo + tarjeta), para que
 * el usuario no note el cambio de tecnología al migrar a nativo.
 *
 * Los valores rgba() del portal se traducen a ARGB: el alfa va primero y
 * se redondea al entero más cercano (p.ej. 0.65 → 0xA6).
 */
object AuthPalette {
    val GradientStart = Color(0xFF240046)   // APP.gradientStart
    val GradientEnd   = Color(0xFF0D001A)   // APP.gradientEnd

    val Card        = Color(0xA6240046)     // rgba(36,0,70,0.65)
    val CardBorder  = Color(0x3D60269E)     // rgba(96,38,158,0.24)

    val Input       = Color(0x12FFFFFF)     // rgba(255,255,255,0.07)
    val InputBorder = Color(0x1FFFFFFF)     // rgba(255,255,255,0.12)
    val InputFocus  = Color(0x66FFB800)     // rgba(255,184,0,0.4)

    val Accent      = Color(0xFFFFB800)     // APP.accent
    val AccentSoft  = Color(0x1FFFB800)     // rgba(255,184,0,0.12)
    val OnAccent    = Color(0xFF240046)     // texto sobre el botón amarillo

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSec     = Color(0xFFD0C4E8)
    val Muted       = Color(0xFF8B72B2)

    val Error       = Color(0xFFD1105A)     // APP.danger
    val ErrorBg     = Color(0x1FD1105A)     // rgba(209,16,90,0.12)
    val ErrorBorder = Color(0x33D1105A)     // rgba(209,16,90,0.2)

    val Success     = Color(0xFF17D1C6)     // APP.success
    val SuccessBg   = Color(0x1F17D1C6)     // rgba(23,209,198,0.12)
    val SuccessBorder = Color(0x3317D1C6)   // rgba(23,209,198,0.2)
}

/**
 * Shell de las pantallas de auth: gradiente vertical, logo con su tagline y
 * la tarjeta centrada (máx. 420 dp) que contiene el formulario.
 */
@Composable
fun AuthScaffold(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(AuthPalette.GradientStart, AuthPalette.GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.luki_logo),
                    contentDescription = "Luki Play",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp),
                )
                Text(
                    text = "TU HOGAR DIGITAL",
                    color = AuthPalette.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AuthPalette.Card)
                    .border(1.dp, AuthPalette.CardBorder, RoundedCornerShape(20.dp))
                    .padding(28.dp),
            ) {
                content()
            }
        }
    }
}

/** Título + subtítulo del formulario, con la métrica del portal. */
@Composable
fun AuthHeading(title: String, subtitle: String?) {
    Text(
        text = title,
        color = AuthPalette.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            color = AuthPalette.Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
    }
}

/** Texto explicativo centrado dentro de la tarjeta (13sp, muted). */
@Composable
fun AuthBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AuthPalette.Muted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    )
}

/** Caja de error del portal: fondo rosa translúcido, borde y texto centrado. */
@Composable
fun AuthErrorBox(message: String) {
    AuthNoticeBox(message, AuthPalette.ErrorBg, AuthPalette.ErrorBorder, AuthPalette.Error)
}

/** Variante de aviso (éxito/info), p.ej. "Código reenviado". */
@Composable
fun AuthInfoBox(message: String) {
    AuthNoticeBox(message, AuthPalette.SuccessBg, AuthPalette.SuccessBorder, AuthPalette.Success)
}

@Composable
private fun AuthNoticeBox(message: String, bg: Color, border: Color, fg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(
            text = message,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Campo del portal: label encima, caja translúcida con borde que se tiñe de
 * ámbar al enfocar, y ojo para revelar la contraseña.
 *
 * Se usa [BasicTextField] en vez de OutlinedTextField porque el Material3
 * por defecto impone su propia altura, label flotante y colores; replicar
 * la métrica exacta del portal sobre él sería pelear con el componente.
 */
@Composable
fun AuthInput(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            text = label,
            color = AuthPalette.TextSec,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AuthPalette.Input)
                .border(
                    width = 1.dp,
                    color = if (focused) AuthPalette.InputFocus else AuthPalette.InputBorder,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AuthPalette.Muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AuthPalette.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(AuthPalette.Accent),
                    visualTransformation =
                        if (secure && !revealed) PasswordVisualTransformation()
                        else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (secure) {
                Text(
                    text = if (revealed) "Ocultar" else "Ver",
                    color = AuthPalette.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { revealed = !revealed }
                        .padding(4.dp),
                )
            }
        }
    }
}

/** Botón primario ámbar con texto violeta oscuro, como el del portal. */
@Composable
fun AuthPrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AuthPalette.Accent.copy(alpha = if (isLoading) 0.6f else 1f))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 15.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = AuthPalette.OnAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = title,
                color = AuthPalette.OnAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** Enlace ámbar centrado (p.ej. "¿Olvidaste tu contraseña?"). */
@Composable
fun AuthLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = AuthPalette.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(4.dp),
        )
    }
}

/** Enlace de retorno con flecha, alineado a la izquierda. */
@Composable
fun AuthBackLink(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 20.dp),
    ) {
        Text("←", color = AuthPalette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AuthPalette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Fila "pregunta + acción" del pie del login. */
@Composable
fun AuthFooterAction(
    question: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = AuthPalette.Muted, fontWeight = FontWeight.Normal)) {
                    append("$question ")
                }
                withStyle(SpanStyle(color = AuthPalette.Accent, fontWeight = FontWeight.Bold)) {
                    append(action)
                }
            },
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(4.dp),
        )
    }
}
