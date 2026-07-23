// tv/compose/TvActivationScreen.kt
package com.luki.play.tv.compose

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.luki.play.R

private val Accent = Color(0xFFFFD000)

/**
 * Activación de TV por QR, réplica de `frontend/app/(auth)/tv-activation.tsx`.
 * Contenido anclado abajo a la izquierda (estilo Apple TV) sobre el degradado
 * de marca.
 *
 * @param onAuthenticated el teléfono activó la sesión → ir al Home de TV.
 */
@Composable
fun TvActivationScreen(
    onAuthenticated: () -> Unit,
    viewModel: TvActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) onAuthenticated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFF190B34), Color(0xFF0D0520), Color(0xFF05020C)))
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, end = 56.dp, bottom = 52.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.luki_logo_h),
                contentDescription = "Luki Play",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(30.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Tu entretenimiento,\nsiempre contigo.",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // QR con padding blanco.
                val qr = rememberQrBitmap(state.activationUrl, sizePx = 260)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp),
                ) {
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = "Código QR de activación", modifier = Modifier.size(130.dp))
                    } else {
                        Box(Modifier.size(130.dp))
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Inicia sesión o regístrate", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = buildAnnotatedString {
                            append("Usa la cámara de tu teléfono o ve a ")
                            withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append("lukiplay.com/activar") }
                            append("\ne ingresa el código ")
                            withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Black, letterSpacing = 3.sp)) { append(state.code) }
                        },
                        color = Color(0xB3FFFFFF),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (state.secsLeft > 60) Color(0xFF4ADE80) else Color(0xFFF43F5E))
                        )
                        Text(
                            text = if (state.error && state.code == "------") "Conectando…"
                            else "Expira en ${formatMmSs(state.secsLeft)}",
                            color = Color(0x73FFFFFF),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** Genera (y memoiza) el bitmap del QR para [content]. */
@Composable
private fun rememberQrBitmap(content: String, sizePx: Int): ImageBitmap? = remember(content, sizePx) {
    runCatching {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1),
        )
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp.asImageBitmap()
    }.getOrNull()
}
