package com.rockstar.orderguardian

import android.content.Context
import com.rockstar.orderguardian.shared.EjectStyle

/** Family-chosen settings, stored on the phone. */
class GuardianSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ejectStyle: EjectStyle
        get() = runCatching {
            EjectStyle.valueOf(prefs.getString(KEY_EJECT_STYLE, EjectStyle.BACK_ONLY.name)!!)
        }.getOrDefault(EjectStyle.BACK_ONLY)
        set(value) {
            prefs.edit().putString(KEY_EJECT_STYLE, value.name).apply()
        }

    companion object {
        private const val PREFS_NAME = "guardian_settings"
        private const val KEY_EJECT_STYLE = "eject_style"
    }
}
