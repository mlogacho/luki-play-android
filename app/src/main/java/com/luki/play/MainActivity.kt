// MainActivity.kt — Router / punto de entrada de la app (después del Splash)
package com.luki.play

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luki.play.mobile.MobileMainActivity
import com.luki.play.tv.TvMainActivity
import com.luki.play.util.DeviceUtils

/**
 * **Router Activity** — primera Activity visible después del Splash.
 *
 * Única responsabilidad: detectar forma de factor (TV vs móvil) y delegar
 * inmediatamente a la Activity correspondiente:
 *
 *  - TV / Google TV → [TvMainActivity]
 *  - Móvil / Tablet → [MobileMainActivity]
 *
 * No tiene layout propio (no llama a setContentView).
 * Se completa en < 1 ms y termina, invisible para el usuario.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = DeviceUtils.isTv(this)

        val target = if (isTv) {
            Intent(this, TvMainActivity::class.java)
        } else {
            Intent(this, MobileMainActivity::class.java)
        }

        startActivity(target)
        finish()
        overridePendingTransition(0, 0)   // Sin animación (transición imperceptible)
    }
}
