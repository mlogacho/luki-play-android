// util/DeviceUtils.kt
package com.luki.play.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics

/**
 * Concrete implementation of [DeviceUtilsContract].
 *
 * Created via [createImpl] to avoid leaking the Activity context.
 * The instance stores [ApplicationContext] internally.
 */
class DeviceUtils private constructor(
    private val context: Context,
    private val activity: Activity?   // Nullable — needed only for PiP
) : DeviceUtilsContract {

    companion object {
        /**
         * Factory — call from any Activity.
         * Passes the Activity reference only for PiP support;
         * all other methods use applicationContext.
         */
        fun createImpl(activity: Activity): DeviceUtils =
            DeviceUtils(activity.applicationContext, activity)

        /** Lightweight TV check without creating a full instance. */
        fun isTv(context: Context): Boolean {
            val mgr = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            return mgr.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }
    }

    override fun isTvDevice(): Boolean = isTv(context)

    override fun getDeviceLabel(): String = Build.MODEL ?: "Android Device"

    @Suppress("DEPRECATION")
    override fun getScreenWidthDp(): Int {
        val metrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay
            .getMetrics(metrics)
        return (metrics.widthPixels / metrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    override fun getScreenHeightDp(): Int {
        val metrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay
            .getMetrics(metrics)
        return (metrics.heightPixels / metrics.density).toInt()
    }

    override fun supportsPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isTvDevice()

    override fun enterPip() {
        if (!supportsPip()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder().build()
            )
        }
    }
}
