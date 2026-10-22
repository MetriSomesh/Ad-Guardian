package com.rockstar.orderguardian

import android.app.Application
import com.google.firebase.FirebaseApp

class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // With google-services.json present the Firebase InitProvider usually
        // does this itself; the guarded call keeps it reliable and harmless
        // when Firebase is not configured.
        if (BuildConfig.FIREBASE_CONFIGURED) {
            runCatching { FirebaseApp.initializeApp(this) }
        }
    }
}
