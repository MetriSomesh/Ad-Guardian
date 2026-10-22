package com.rockstar.orderguardian

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

/**
 * The only thing the grandfather is meant to read day to day: a short,
 * large-text banner at the bottom of the screen after an eject.
 *
 * It is deliberately NOT a full-screen block: the eject itself (Back/Home)
 * does the stopping. The banner only explains what just happened, in English
 * with a Hindi line under it. It never traps touches.
 */
object NoticeOverlay {

    /**
     * Overlay notice banner was disabled per user request:
     * ejections and blocks happen silently without showing the green banner.
     */
    fun show(context: Context, message: String) {
        // No-op: silent eject
    }
}
