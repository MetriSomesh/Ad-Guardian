package com.rockstar.orderguardian.approver

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Wakes the approver when a new request arrives (sent by the guardian side / a future Cloud Function). */
class ApproverMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Order approval requested"
        val body = message.notification?.body ?: message.data["body"] ?: "Open Order Approver to decide."
        val notification = NotificationCompat.Builder(this, CHANNEL_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(4001, notification)
        }
    }

    companion object {
        const val CHANNEL_REQUESTS = "requests"
    }
}
