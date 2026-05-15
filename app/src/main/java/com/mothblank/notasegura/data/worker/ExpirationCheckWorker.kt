// Em data/worker/ExpirationCheckWorker.kt

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
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ExpirationCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Obtém os repositórios a partir da nossa classe Application
        val repository = (applicationContext as NotaSeguraApplication).repository
        val paymentRepository = (applicationContext as NotaSeguraApplication).paymentRepository

        val today = LocalDate.now()
        val thirtyDaysFromNow = today.plusDays(30)
        val threeDaysFromNow = today.plusDays(3)

        // Busca no banco por itens que expiram nos próximos 30 dias
        val expiringItems = repository.getAllItems().first().filter {
            it.expirationDate.isAfter(today.minusDays(1)) && it.expirationDate.isBefore(thirtyDaysFromNow)
        }

        // Busca no banco por pagamentos pendentes nos próximos 3 dias
        val pendingPayments = paymentRepository.getAllPayments().first().filter {
            !it.isPaid && it.dueDate.isAfter(today.minusDays(1)) && it.dueDate.isBefore(threeDaysFromNow)
        }

        if (expiringItems.isNotEmpty()) {
            showNotification(101, "Lembrete de Garantia", 
                if (expiringItems.size == 1) "Você tem 1 item expirando nos próximos 30 dias." 
                else "Você tem ${expiringItems.size} itens expirando nos próximos 30 dias.")
        }

        if (pendingPayments.isNotEmpty()) {
            showNotification(102, "Lembrete de Pagamento", 
                if (pendingPayments.size == 1) "Você tem 1 pagamento vencendo em breve." 
                else "Você tem ${pendingPayments.size} pagamentos vencendo em breve.")
        }

        return Result.success()
        }

        private fun showNotification(id: Int, title: String, contentText: String) {
        // Constrói a notificação
        val builder = NotificationCompat.Builder(applicationContext, "WARRANTY_REMINDERS")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use o ícone do seu app
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Verifica se tem permissão antes de tentar notificar
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
        }
        }
        }