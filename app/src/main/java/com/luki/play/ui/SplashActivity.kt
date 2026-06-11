package com.luki.play.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Delegar en RouterActivity, que detecta TV vs móvil y abre la Activity
        // correcta (TvMainActivity / MobileMainActivity). Antes saltaba directo a
        // MainActivity (legacy/fallback), que no arranca bien en móvil → la app
        // "no abría" en el teléfono.
        startActivity(Intent(this, RouterActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }
}
