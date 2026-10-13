package com.mothblank.notasegura.data.worker

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.mothblank.notasegura.R
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.util.ReminderDeliveryState
import com.mothblank.notasegura.util.ReminderPreferences
import java.time.LocalDate

/**
 * Single reconciliation path for both the daily worker and notification actions.
 * Replaces the shared payment notification when any outstanding bill changes and
 * only cancels it after confirming no in-window unpaid obligations remain.
 */
internal object PaymentReminderReconciler {
    suspend fun reconcile(context: Context, repository: PaymentRepository, today: LocalDate,
        forceRefresh: Boolean = false
    ) {
        // A paid recurring installment must not hide another month's debt.
        repository.refreshOccurrences(today.plusMonths(1))
        val paymentLeadDays = ReminderPreferences.paymentLeadDays(context)
        val notifications = ReminderNotificationPublisher(context)
        val pendingPayments = if (paymentLeadDays > 0) {
            repository.getPendingPaymentsDueOnOrBefore(
                endDate = today.plusDays(paymentLeadDays.toLong())
            )
        } else {
            emptyList()
        }

        // An action may dismiss an earlier notification even when its intent is stale.
        // Explicit refresh must repost a surviving reminder regardless of cached signature.
        if (forceRefresh) ReminderDeliveryState.clear(context, ReminderDeliveryState.PAYMENT)

        if (pendingPayments.isNotEmpty()) {
            val overdue = pendingPayments.count { it.dueDate.isBefore(today) }
            val dueSoon = pendingPayments.size - overdue
            val overdueLabel = if (overdue > 0) {
                context.resources.getQuantityString(
                    R.plurals.notification_payment_overdue_fragment,
                    overdue,
                    overdue
                )
            } else {
                ""
            }
            val dueWindow = context.resources.getQuantityString(
                R.plurals.reminder_window,
                paymentLeadDays,
                paymentLeadDays
            )
            val dueSoonLabel = if (dueSoon > 0) {
                context.resources.getQuantityString(
                    R.plurals.notification_payment_due_fragment,
                    dueSoon,
                    dueSoon,
                    dueWindow
                )
            } else {
                ""
            }
            val message = if (overdue > 0 && dueSoon > 0) {
                context.getString(
                    R.string.notification_payment_combined,
                    overdueLabel,
                    dueSoonLabel
                )
            } else {
                context.getString(
                    R.string.notification_payment_single,
                    if (overdue > 0) overdueLabel else dueSoonLabel
                )
            }
            val destination = if (pendingPayments.size == 1) {
                notifications.detailUri("payment", pendingPayments.single().id)
            } else {
                Uri.parse("notasegura://payments")
            }

            val signature = ReminderDeliveryState.signature(
                paymentLeadDays,
                pendingPayments.map { payment ->
                    "${payment.id}:${payment.dueDate}:" +
                        ReminderDeliveryState.urgencyBucket(
                            payment.dueDate.toEpochDay() - today.toEpochDay()
                        )
                }
            )
            if (ReminderDeliveryState.needsDelivery(
                    context, ReminderDeliveryState.PAYMENT, signature
                )
            ) {
                if (notifications.showNotification(
                        id = ExpirationCheckWorker.PAYMENT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_payment_title),
                        contentText = message,
                        destination = destination,
                        actionLabel = context.getString(
                            if (pendingPayments.size == 1) {
                                R.string.notification_action_view_payment
                            } else {
                                R.string.notification_action_view_payments
                            }
                        ),
                        markPaidPaymentId = pendingPayments.singleOrNull()?.id
                )) {
                    ReminderDeliveryState.delivered(
                        context, ReminderDeliveryState.PAYMENT, signature
                    )
                }
            }
        } else {
            NotificationManagerCompat.from(context)
                .cancel(ExpirationCheckWorker.PAYMENT_NOTIFICATION_ID)
            ReminderDeliveryState.clear(context, ReminderDeliveryState.PAYMENT)
        }
    }
}
