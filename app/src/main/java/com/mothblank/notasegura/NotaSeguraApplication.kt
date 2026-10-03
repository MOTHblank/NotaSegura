package com.mothblank.notasegura

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mothblank.notasegura.data.backup.BackupArchiveManager
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.ocr.ReceiptOcrEngine
import com.mothblank.notasegura.data.repository.PaymentRepositoryImpl
import com.mothblank.notasegura.data.repository.PurchaseRepositoryImpl
import com.mothblank.notasegura.data.storage.PurchaseDocumentStore
import com.mothblank.notasegura.data.worker.ExpirationCheckWorker
import com.mothblank.notasegura.domain.repository.PaymentRepository
import com.mothblank.notasegura.domain.repository.PurchaseRepository
import com.mothblank.notasegura.util.TemporaryFileMaintenance
import java.util.concurrent.TimeUnit

class NotaSeguraApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "nota-segura-v2.db"
        ).build()
    }

    val purchaseRepository: PurchaseRepository by lazy {
        PurchaseRepositoryImpl(database.purchaseDao())
    }

    val paymentRepository: PaymentRepository by lazy {
        PaymentRepositoryImpl(database.paymentDao())
    }

    val purchaseDocumentStore: PurchaseDocumentStore by lazy {
        PurchaseDocumentStore(applicationContext, purchaseRepository)
    }

    val receiptOcrEngine: ReceiptOcrEngine by lazy {
        ReceiptOcrEngine(applicationContext)
    }

    val backupArchiveManager: BackupArchiveManager by lazy {
        BackupArchiveManager(applicationContext, database)
    }

    override fun onCreate() {
        super.onCreate()
        TemporaryFileMaintenance.cleanup(applicationContext)
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
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
