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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mothblank.notasegura.MainActivity
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.R
import com.mothblank.notasegura.util.ReminderPreferences
import java.time.LocalDate

class ExpirationCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NotaSeguraApplication
        val today = LocalDate.now()
        val warrantyLeadDays = ReminderPreferences.warrantyLeadDays(applicationContext)
        val paymentLeadDays = ReminderPreferences.paymentLeadDays(applicationContext)

        val expiringPurchases = if (warrantyLeadDays > 0) {
            app.purchaseRepository.getPurchasesExpiringBetween(
                startDate = today,
                endDate = today.plusDays(warrantyLeadDays.toLong())
            )
        } else {
            emptyList()
        }

        val pendingPayments = if (paymentLeadDays > 0) {
            app.paymentRepository.getPendingPaymentsDueOnOrBefore(
                endDate = today.plusDays(paymentLeadDays.toLong())
            )
        } else {
            emptyList()
        }

        if (expiringPurchases.isNotEmpty()) {
            val window = applicationContext.resources.getQuantityString(
                R.plurals.reminder_window,
                warrantyLeadDays,
                warrantyLeadDays
            )
            val destination = if (expiringPurchases.size == 1) {
                detailUri("purchase", expiringPurchases.single().id)
            } else {
                Uri.parse("notasegura://purchases")
            }

            showNotification(
                id = WARRANTY_NOTIFICATION_ID,
                title = applicationContext.getString(R.string.notification_warranty_title),
                contentText = applicationContext.resources.getQuantityString(
                    R.plurals.notification_warranty_expiring,
                    expiringPurchases.size,
                    expiringPurchases.size,
                    window
                ),
                destination = destination,
                actionLabel = applicationContext.getString(
                    if (expiringPurchases.size == 1) {
                        R.string.notification_action_view_purchase
                    } else {
                        R.string.notification_action_view_purchases
                    }
                )
            )
        } else {
            NotificationManagerCompat.from(applicationContext)
                .cancel(WARRANTY_NOTIFICATION_ID)
        }

        if (pendingPayments.isNotEmpty()) {
            val overdue = pendingPayments.count { it.dueDate.isBefore(today) }
            val dueSoon = pendingPayments.size - overdue
            val overdueLabel = if (overdue > 0) {
                applicationContext.resources.getQuantityString(
                    R.plurals.notification_payment_overdue_fragment,
                    overdue,
                    overdue
                )
            } else {
                ""
            }
            val dueWindow = applicationContext.resources.getQuantityString(
                R.plurals.reminder_window,
                paymentLeadDays,
                paymentLeadDays
            )
            val dueSoonLabel = if (dueSoon > 0) {
                applicationContext.resources.getQuantityString(
                    R.plurals.notification_payment_due_fragment,
                    dueSoon,
                    dueSoon,
                    dueWindow
                )
            } else {
                ""
            }
            val message = if (overdue > 0 && dueSoon > 0) {
                applicationContext.getString(
                    R.string.notification_payment_combined,
                    overdueLabel,
                    dueSoonLabel
                )
            } else {
                applicationContext.getString(
                    R.string.notification_payment_single,
                    if (overdue > 0) overdueLabel else dueSoonLabel
                )
            }
            val destination = if (pendingPayments.size == 1) {
                detailUri("payment", pendingPayments.single().id)
            } else {
                Uri.parse("notasegura://payments")
            }

            showNotification(
                id = PAYMENT_NOTIFICATION_ID,
                title = applicationContext.getString(R.string.notification_payment_title),
                contentText = message,
                destination = destination,
                actionLabel = applicationContext.getString(
                    if (pendingPayments.size == 1) {
                        R.string.notification_action_view_payment
                    } else {
                        R.string.notification_action_view_payments
                    }
                ),
                markPaidPaymentId = pendingPayments.singleOrNull()?.id
            )
        } else {
            NotificationManagerCompat.from(applicationContext)
                .cancel(PAYMENT_NOTIFICATION_ID)
        }

        return Result.success()
    }

    private fun detailUri(kind: String, id: String): Uri =
        Uri.Builder()
            .scheme("notasegura")
            .authority(kind)
            .appendPath(id)
            .build()

    private fun showNotification(
        id: Int,
        title: String,
        contentText: String,
        destination: Uri,
        actionLabel: String,
        markPaidPaymentId: String? = null
    ) {
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            destination.toString().hashCode() and Int.MAX_VALUE,
            Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = destination
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
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
                applicationContext,
                "paid:$paymentId".hashCode() and Int.MAX_VALUE,
                Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                    action = ReminderActionReceiver.ACTION_MARK_PAYMENT_PAID
                    putExtra(ReminderActionReceiver.EXTRA_PAYMENT_ID, paymentId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                0,
                applicationContext.getString(R.string.notification_action_mark_paid),
                markPaidIntent
            )
        }

        val canNotify =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
        }
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "REMINDERS"
        const val WARRANTY_NOTIFICATION_ID = 101
        const val PAYMENT_NOTIFICATION_ID = 102
    }
}
