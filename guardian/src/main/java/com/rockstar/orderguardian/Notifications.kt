package com.rockstar.orderguardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rockstar.orderguardian.shared.ApprovalRequest

/** Notification channels and the two notifications the guardian actually sends. */
object Notifications {

    const val CHANNEL_PROTECTION = "protection"
    const val CHANNEL_REQUESTS = "requests"
    private const val ID_PROTECTION = 1001
    private const val ID_REQUEST_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROTECTION,
                context.getString(R.string.channel_protection),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REQUESTS,
                context.getString(R.string.channel_requests),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** The persistent foreground-service notification. Wording is for the grandfather: plain and calm. */
    fun protectionNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Shopping protection is ON")
            .setContentText("Orders on Amazon and Flipkart will be checked with family first.")
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)
            .build()

    fun requestCreatedNotification(context: Context, request: ApprovalRequest, liveMode: Boolean) {
        val body = buildString {
            append(request.appLabel)
            request.productText?.let { append(" — ", it.take(60)) }
            request.priceText?.let { append(" — ", it) }
            append(
                if (liveMode) {
                    "\nWaiting for a family decision in the Approver app."
                } else {
                    "\nOn-device test mode: decide in the Order Guardian app on this phone."
                },
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Order paused — approval requested")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentText(body)
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.notify(ID_REQUEST_BASE + (request.id.hashCode() and 0x3FF), notification)
        }
    }

    private fun mainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
