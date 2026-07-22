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
 * El bundle del portal (lukiplay.com) usa sintaxis que motores viejos no
 * pueden *parsear*: el archivo entero lanza SyntaxError y la página queda
 * vacía, sin error de red. Antes de cargar el portal se comprueba la versión
 * y, si no alcanza, se muestra una pantalla accionable en lugar de un WebView
 * mudo.
 */
object WebViewSupport {

    /**
     * Mínimo major de Chromium que el bundle del portal puede parsear.
     *
     * 85 = operadores de asignación lógica (`??=`, `||=`, `&&=`), Chrome 85.
     * Verificado el 2026-07-22 sobre el bundle en producción
     * (`_expo/static/js/web/entry-*.js`): 4 ocurrencias, todas código real de
     * Expo Router (p.ej. `u.layout??=[]`, `c||=s.StackRouter`), no dentro de
     * strings — un análisis previo las descartó como falso positivo y por eso
     * el piso quedó en 80. Confirmado en emulador Android 11 con WebView
     * 83.0.4103.106: `Uncaught SyntaxError: Unexpected token '='` y pantalla
     * en blanco.
     *
     * Los campos privados de clase (`#x`, Chrome 84) también aparecen, pero
     * quedan cubiertos por este piso.
     *
     * Ojo: el bundle usa además `Object.hasOwn` (Chrome 93). Es API de
     * runtime, no sintaxis, así que no impide parsear y solo fallaría en las
     * rutas que la ejecuten; no se sube el piso a 93 sin evidencia de rotura
     * real, para no bloquear equipos que funcionan. El BlankPageWatchdog
     * cubre ese caso.
     *
     * No subir sin probar contra un motor real de la versión a excluir.
     */
    const val MIN_CHROMIUM_MAJOR = 85

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
