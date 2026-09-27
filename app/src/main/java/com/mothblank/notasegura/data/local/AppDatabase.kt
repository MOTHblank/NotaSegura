package com.mothblank.notasegura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.WarrantyItem

@Database(
    entities = [WarrantyItem::class, Payment::class],
    version = 3
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun warrantyItemDao(): WarrantyItemDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE warranty_items ADD COLUMN imagePath TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        dueDate INTEGER NOT NULL,
                        isPaid INTEGER NOT NULL,
                        isRecurring INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payments_title_dueDate ON payments(title, dueDate)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE payments_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        amountCents INTEGER NOT NULL,
                        dueDate INTEGER NOT NULL,
                        isPaid INTEGER NOT NULL,
                        paidAt INTEGER,
                        recurrenceMonths INTEGER,
                        recurrenceAnchorDay INTEGER,
                        seriesId TEXT,
                        generatedFromId TEXT
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO payments_new (
                        id, title, amountCents, dueDate, isPaid, paidAt,
                        recurrenceMonths, recurrenceAnchorDay, seriesId, generatedFromId
                    )
                    SELECT
                        id,
                        title,
                        CAST(ROUND(amount * 100.0) AS INTEGER),
                        dueDate,
                        isPaid,
                        NULL,
                        CASE WHEN isRecurring = 1 THEN 1 ELSE NULL END,
                        NULL,
                        CASE WHEN isRecurring = 1 THEN id ELSE NULL END,
                        NULL
                    FROM payments
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE payments")
                database.execSQL("ALTER TABLE payments_new RENAME TO payments")
                database.execSQL(
                    "CREATE INDEX index_payments_seriesId_dueDate ON payments(seriesId, dueDate)"
                )
            }
        }
    }
}
