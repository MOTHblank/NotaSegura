package com.mothblank.notasegura.data.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mothblank.notasegura.MainActivity
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.R
import java.time.LocalDate

class ExpirationCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NotaSeguraApplication
        val today = LocalDate.now()

        val expiringPurchases = app.purchaseRepository.getPurchasesExpiringBetween(
            startDate = today,
            endDate = today.plusDays(30)
        )
        val pendingPayments = app.paymentRepository.getPendingPaymentsDueOnOrBefore(
            endDate = today.plusDays(3)
        )

        if (expiringPurchases.isNotEmpty()) {
            showNotification(
                101,
                applicationContext.getString(R.string.notification_warranty_title),
                applicationContext.resources.getQuantityString(
                    R.plurals.notification_warranty_expiring,
                    expiringPurchases.size,
                    expiringPurchases.size
                )
            )
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
            val dueSoonLabel = if (dueSoon > 0) {
                applicationContext.resources.getQuantityString(
                    R.plurals.notification_payment_due_fragment,
                    dueSoon,
                    dueSoon
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

            showNotification(
                102,
                applicationContext.getString(R.string.notification_payment_title),
                message
            )
        }

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, contentText: String) {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "REMINDERS")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

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
}
