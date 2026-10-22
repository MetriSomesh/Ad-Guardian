package com.rockstar.orderguardian

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Approval-result wake-up when Firebase is configured: the approver app (or a
 * future Cloud Function) sends a data/notification message to this device.
 * The decision itself is always re-read from the repository — a push message
 * alone never unblocks a checkout.
 */
class GuardianMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Order Guardian"
        val body = message.notification?.body ?: message.data["body"] ?: "An order decision has been made."
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(3001, notification)
        }
    }
}
