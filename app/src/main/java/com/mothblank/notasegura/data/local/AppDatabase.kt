package com.mothblank.notasegura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.Purchase

@Database(
    entities = [Purchase::class, Attachment::class, Payment::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun paymentDao(): PaymentDao
}
