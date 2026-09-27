package com.mothblank.notasegura

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.repository.PaymentRepositoryImpl
import com.mothblank.notasegura.data.repository.WarrantyRepositoryImpl
import com.mothblank.notasegura.data.storage.WarrantyDocumentStore
import com.mothblank.notasegura.data.worker.ExpirationCheckWorker
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.domain.repository.WarrantyRepository
import java.util.concurrent.TimeUnit

class NotaSeguraApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "nota-segura-db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    val repository: WarrantyRepository by lazy {
        WarrantyRepositoryImpl(database.warrantyItemDao())
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepositoryImpl(database.paymentDao())
    }

    val warrantyDocumentStore: WarrantyDocumentStore by lazy {
        WarrantyDocumentStore(applicationContext, repository)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleDailyExpirationCheck()
    }

    private fun scheduleDailyExpirationCheck() {
        val repeatingRequest =
            PeriodicWorkRequestBuilder<ExpirationCheckWorker>(1, TimeUnit.DAYS).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "daily_expiration_check",
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "REMINDERS",
            "Lembretes NotaSegura",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Garantias prestes a expirar e pagamentos próximos do vencimento"
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
