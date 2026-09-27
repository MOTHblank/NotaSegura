package com.mothblank.notasegura.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

        val expiringItems = app.repository.getItemsExpiringBetween(
            startDate = today,
            endDate = today.plusDays(30)
        )
        val pendingPayments = app.paymentRepository.getPendingPaymentsDueBetween(
            startDate = today,
            endDate = today.plusDays(3)
        )

        if (expiringItems.isNotEmpty()) {
            showNotification(
                101,
                "Lembrete de Garantia",
                if (expiringItems.size == 1) {
                    "Você tem 1 item expirando nos próximos 30 dias."
                } else {
                    "Você tem ${expiringItems.size} itens expirando nos próximos 30 dias."
                }
            )
        }

        if (pendingPayments.isNotEmpty()) {
            showNotification(
                102,
                "Lembrete de Pagamento",
                if (pendingPayments.size == 1) {
                    "Você tem 1 pagamento vencendo em breve."
                } else {
                    "Você tem ${pendingPayments.size} pagamentos vencendo em breve."
                }
            )
        }

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, contentText: String) {
        val builder = NotificationCompat.Builder(applicationContext, "REMINDERS")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (
            ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
        }
    }
}
