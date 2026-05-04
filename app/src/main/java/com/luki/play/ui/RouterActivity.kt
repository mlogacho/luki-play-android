package com.luki.play.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.luki.play.mobile.MobileMainActivity
import com.luki.play.tv.TvMainActivity
import com.luki.play.util.Constants
import com.luki.play.util.DeviceUtils

/**
 * Router Activity — detects TV vs mobile and delegates immediately.
 * No layout; invisible to the user.
 */
class RouterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = DeviceUtils.isTv(this)
        val targetIntent = if (isTv) {
            Intent(this, TvMainActivity::class.java)
        } else {
            Intent(this, MobileMainActivity::class.java)
        }

        targetIntent.putExtra(Constants.EXTRA_IS_TV, isTv)
        startActivity(targetIntent)
        finish()
    }
}
