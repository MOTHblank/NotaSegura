package com.mothblank.notasegura.data.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mothblank.notasegura.MainActivity
import com.mothblank.notasegura.R

/** Shared, permission-aware renderer used for warranty and payment reminders. */
internal class ReminderNotificationPublisher(private val context: Context) {
    fun detailUri(kind: String, id: String): Uri =
        Uri.Builder()
            .scheme("notasegura")
            .authority(kind)
            .appendPath(id)
            .build()

    fun showNotification(
        id: Int,
        title: String,
        contentText: String,
        destination: Uri,
        actionLabel: String,
        markPaidPaymentId: String? = null
    ): Boolean {
        val contentIntent = PendingIntent.getActivity(
            context,
            destination.toString().hashCode() and Int.MAX_VALUE,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = destination
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ExpirationCheckWorker.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntent)
            .addAction(0, actionLabel, contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        markPaidPaymentId?.let { paymentId ->
            val markPaidIntent = PendingIntent.getBroadcast(
                context,
                "paid:$paymentId".hashCode() and Int.MAX_VALUE,
                Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ReminderActionReceiver.ACTION_MARK_PAYMENT_PAID
                    putExtra(ReminderActionReceiver.EXTRA_PAYMENT_ID, paymentId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                0,
                context.getString(R.string.notification_action_mark_paid),
                markPaidIntent
            )
        }

        val canNotify =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                try {
                    manager.notify(id, builder.build())
                    return true
                } catch (_: SecurityException) {
                    // Permission may have been revoked between the check and notify.
                }
            }
        }
        return false
    }


}
