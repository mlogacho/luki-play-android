// util/CrashlyticsTree.kt
package com.luki.play.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Árbol de Timber para release: reenvía WARN/ERROR a Crashlytics como
 * breadcrumbs y registra los Throwable como non-fatals.
 *
 * Solo debe plantarse cuando Firebase está inicializado (hay
 * google-services.json en el build); LukiApplication hace esa comprobación.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        crashlytics.log(if (tag != null) "[$tag] $message" else message)
        if (t != null) crashlytics.recordException(t)
    }
}
