// Em com/mothblank/notasegura/NotaSeguraApplication.kt

package com.mothblank.notasegura

import android.app.Application
import androidx.room.Room
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.repository.WarrantyRepositoryImpl
import com.mothblank.notasegura.data.repository.PaymentRepositoryImpl
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import com.mothblank.notasegura.domain.repository.PaymentRepository
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.*
import com.mothblank.notasegura.data.worker.ExpirationCheckWorker
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class NotaSeguraApplication : Application() {

    val scope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy {

        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "nota-segura-db"
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleDailyExpirationCheck()
    }

    private fun scheduleDailyExpirationCheck() {
        // Cria a requisição para rodar uma vez por dia
        val repeatingRequest = PeriodicWorkRequestBuilder<ExpirationCheckWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "daily_expiration_check", // Nome único para este trabalho
            ExistingPeriodicWorkPolicy.KEEP, // Se já estiver agendado, não faz nada
            repeatingRequest
        )
    }
    private fun createNotificationChannel() {
        // A criação do canal só é necessária no Android 8 (API 26) ou superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Garantia"
            val descriptionText = "Notificações sobre garantias prestes a expirar"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("WARRANTY_REMINDERS", name, importance).apply {
                description = descriptionText
            }
            // Registra o canal no sistema
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    val repository: WarrantyRepository by lazy {
        WarrantyRepositoryImpl(database.warrantyItemDao())
    }
    val paymentRepository: PaymentRepository by lazy {
        PaymentRepositoryImpl(database.paymentDao())
    }
}