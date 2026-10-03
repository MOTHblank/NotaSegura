package com.mothblank.notasegura.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.mothblank.notasegura.NotaSeguraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_PAYMENT_PAID) return

        val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as NotaSeguraApplication

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val payment = app.paymentRepository.getPaymentById(paymentId)
                if (payment != null && !payment.isPaid) {
                    app.paymentRepository.setPaidStatus(
                        payment = payment,
                        isPaid = true,
                        date = LocalDate.now()
                    )
                }
                NotificationManagerCompat.from(context)
                    .cancel(ExpirationCheckWorker.PAYMENT_NOTIFICATION_ID)
            } catch (_: Exception) {
                // Keep the notification visible so the user can retry or open the payment.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_PAYMENT_PAID =
            "com.mothblank.notasegura.action.MARK_PAYMENT_PAID"
        const val EXTRA_PAYMENT_ID = "payment_id"
    }
}
