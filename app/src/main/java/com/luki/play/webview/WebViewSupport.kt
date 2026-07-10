// webview/WebViewSupport.kt
package com.luki.play.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.luki.play.R

/**
 * Diagnóstico y respaldo del motor WebView del sistema.
 *
 * El portal web (lukiplay.com) usa optional chaining / nullish coalescing en
 * su bundle, sintaxis que requiere Chromium ≥ 80 (feb 2020) para *parsearse*;
 * en motores más viejos el bundle lanza SyntaxError y la página queda vacía
 * sin error de red. Antes de cargar el portal se comprueba la versión y, si
 * no alcanza, se muestra una pantalla accionable en lugar de un WebView mudo.
 */
object WebViewSupport {

    /**
     * Mínimo major de Chromium que el bundle del portal puede parsear.
     * Verificado empíricamente: el portal renderiza en WebView 83 (emulador
     * Android 11); el piso lo pone el optional chaining (80). No subir sin
     * probar contra un motor real de la versión que se quiera excluir.
     */
    const val MIN_CHROMIUM_MAJOR = 80

    private const val WEBVIEW_PLAY_URI =
        "market://details?id=com.google.android.webview"

    /** Versión del paquete WebView activo ("149.0.7827.159"), o null. */
    fun versionName(context: Context): String? = runCatching {
        androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName
    }.getOrNull()

    /** Major de Chromium del WebView activo, o null si no se puede leer. */
    fun chromiumMajor(context: Context): Int? =
        versionName(context)?.substringBefore('.')?.toIntOrNull()

    /**
     * true si el WebView es demasiado viejo para el portal. Si la versión no
     * se puede leer se asume soportado: mejor intentar cargar que bloquear un
     * equipo sano por un fallo de detección (el BlankPageWatchdog cubre el
     * resto).
     */
    fun isOutdated(context: Context): Boolean {
        val major = chromiumMajor(context) ?: return false
        return major < MIN_CHROMIUM_MAJOR
    }

    /** Abre la ficha de Android System WebView en la Play Store del equipo. */
    fun openWebViewInPlayStore(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(WEBVIEW_PLAY_URI))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /**
     * Pantalla de respaldo a pantalla completa, construida en código porque se
     * usa justo cuando el WebView (y el layout que lo contiene) no se pudo
     * crear o no sirve. Incluye botón para abrir la ficha del WebView en Play
     * Store, con foco inicial para que sea operable con D-pad en TV.
     */
    fun showFallbackScreen(activity: Activity, message: String) {
        val density = activity.resources.displayMetrics.density
        val pad = (24 * density).toInt()

        val msgView = TextView(activity).apply {
            text = message
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val button = Button(activity).apply {
            text = activity.getString(R.string.btn_open_play_store)
            isFocusable = true
            setOnClickListener { openWebViewInPlayStore(activity) }
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#240046"))
            addView(
                msgView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            )
        }
        activity.setContentView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        button.requestFocus()
    }
}
