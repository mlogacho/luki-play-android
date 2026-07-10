// webview/BlankPageWatchdog.kt
package com.luki.play.webview

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import timber.log.Timber

/**
 * Detecta la "pantalla negra": página que terminó de cargar (HTTP OK) pero
 * cuyo JavaScript murió sin montar nada en el DOM — típico de motores WebView
 * viejos donde el bundle del portal ni siquiera parsea. Sin esto el usuario
 * queda mirando un fondo vacío sin ningún mensaje.
 *
 * Uso: [onPageFinished] desde el callback homónimo del WebViewClient,
 * [cancel] al iniciar otra navegación, al mostrar un error o en onDestroy.
 */
class BlankPageWatchdog(
    private val webView: WebView,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val onBlankPage: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Programa la comprobación para [timeoutMs] después del fin de carga. */
    fun onPageFinished() {
        cancel()
        pending = Runnable { probe() }.also { handler.postDelayed(it, timeoutMs) }
    }

    fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private fun probe() {
        pending = null
        webView.evaluateJavascript(PROBE_JS) { result ->
            // "true" ⇢ la página montó contenido. Cualquier otra cosa (false,
            // null por excepción, motor que ni evalúa) cuenta como página vacía.
            if (result != "true") {
                Timber.tag(TAG).w("Página en blanco tras la carga (probe=%s)", result)
                onBlankPage()
            }
        }
    }

    companion object {
        private const val TAG = "BlankPageWatchdog"
        private const val DEFAULT_TIMEOUT_MS = 12_000L

        // ES5 a propósito: debe poder ejecutarse hasta en los motores viejos
        // que rompen el bundle moderno del portal. Contenido montado = hay
        // texto visible en el body y el overlay del splash del portal (si
        // existió) ya fue retirado.
        private val PROBE_JS = """
            (function() {
              try {
                var overlay = document.getElementById('luki-splash-overlay');
                var hasText = !!(document.body && document.body.innerText &&
                                 document.body.innerText.replace(/\s+/g, '').length > 0);
                return !overlay && hasText;
              } catch (e) {
                return false;
              }
            })();
        """.trimIndent()
    }
}
