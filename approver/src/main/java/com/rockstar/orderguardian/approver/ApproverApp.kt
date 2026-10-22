package com.rockstar.orderguardian.approver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp

class ApproverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    ApproverMessagingService.CHANNEL_REQUESTS,
                    getString(R.string.channel_requests),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        if (BuildConfig.FIREBASE_CONFIGURED) {
            runCatching { FirebaseApp.initializeApp(this) }
        }
    }
}
