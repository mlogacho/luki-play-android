package com.luki.play.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luki.play.data.config.FeatureFlags
import com.luki.play.mobile.MobileComposeActivity
import com.luki.play.mobile.MobileMainActivity
import com.luki.play.tv.TvComposeActivity
import com.luki.play.tv.TvMainActivity
import com.luki.play.util.Constants
import com.luki.play.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Router Activity — detects TV vs mobile and delegates immediately.
 * No layout; invisible to the user.
 *
 * El interruptor nativo/WebView ya no es un `BuildConfig` fijo: lo decide
 * [FeatureFlags] (Remote Config) para poder hacer rollout gradual y
 * kill-switch sin recompilar. Sin Firebase, cae al valor compilado.
 */
@AndroidEntryPoint
class RouterActivity : AppCompatActivity() {

    @Inject lateinit var featureFlags: FeatureFlags

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = DeviceUtils.isTv(this)
        val nativeHome = featureFlags.nativeHomeEnabled
        val targetIntent = when {
            isTv && nativeHome   -> Intent(this, TvComposeActivity::class.java)
            isTv                 -> Intent(this, TvMainActivity::class.java)
            nativeHome           -> Intent(this, MobileComposeActivity::class.java)
            else                 -> Intent(this, MobileMainActivity::class.java)
        }

        targetIntent.putExtra(Constants.EXTRA_IS_TV, isTv)
        startActivity(targetIntent)
        finish()
    }
}
