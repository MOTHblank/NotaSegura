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
import com.mothblank.notasegura.util.ReminderDeliveryState
import java.time.LocalDate

class ExpirationCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NotaSeguraApplication
        val today = LocalDate.now()
        val notifier = ReminderNotificationPublisher(applicationContext)
        val warrantyLeadDays = ReminderPreferences.warrantyLeadDays(applicationContext)

        val expiringPurchases = if (warrantyLeadDays > 0) {
            app.purchaseRepository.getPurchasesExpiringBetween(
                startDate = today,
                endDate = today.plusDays(warrantyLeadDays.toLong())
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
                notifier.detailUri("purchase", expiringPurchases.single().id)
            } else {
                Uri.parse("notasegura://purchases")
            }

            val signature = ReminderDeliveryState.signature(
                warrantyLeadDays,
                expiringPurchases.map { purchase ->
                    "${purchase.id}:${purchase.warrantyEndDate}:" +
                        ReminderDeliveryState.urgencyBucket(
                            requireNotNull(purchase.warrantyEndDate).toEpochDay() - today.toEpochDay()
                        )
                }
            )
            if (ReminderDeliveryState.needsDelivery(
                    applicationContext, ReminderDeliveryState.WARRANTY, signature
                )
            ) {
                if (notifier.showNotification(
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
                )) {
                    ReminderDeliveryState.delivered(
                        applicationContext, ReminderDeliveryState.WARRANTY, signature
                    )
                }
            }
        } else {
            NotificationManagerCompat.from(applicationContext)
                .cancel(WARRANTY_NOTIFICATION_ID)
            ReminderDeliveryState.clear(applicationContext, ReminderDeliveryState.WARRANTY)
        }

        PaymentReminderReconciler.reconcile(applicationContext, app.paymentRepository, today)

        return Result.success()
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "REMINDERS"
        const val WARRANTY_NOTIFICATION_ID = 101
        const val PAYMENT_NOTIFICATION_ID = 102
    }
}
