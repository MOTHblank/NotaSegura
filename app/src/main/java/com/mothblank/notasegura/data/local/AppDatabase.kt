package com.mothblank.notasegura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.Purchase

@Database(
    entities = [Purchase::class, Attachment::class, Payment::class],
    version = 4
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun purchaseDao(): PurchaseDao
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS purchases (
                        id TEXT NOT NULL PRIMARY KEY,
                        productName TEXT NOT NULL,
                        merchant TEXT,
                        purchaseValueCents INTEGER,
                        purchaseDate INTEGER NOT NULL,
                        warrantyEndDate INTEGER,
                        category TEXT NOT NULL,
                        modelNumber TEXT,
                        serialNumber TEXT,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX index_purchases_productName ON purchases(productName)")
                database.execSQL("CREATE INDEX index_purchases_merchant ON purchases(merchant)")
                database.execSQL("CREATE INDEX index_purchases_purchaseDate ON purchases(purchaseDate)")
                database.execSQL("CREATE INDEX index_purchases_warrantyEndDate ON purchases(warrantyEndDate)")

                database.execSQL(
                    """
                    INSERT INTO purchases (
                        id, productName, merchant, purchaseValueCents,
                        purchaseDate, warrantyEndDate, category,
                        modelNumber, serialNumber, notes, createdAt, updatedAt
                    )
                    SELECT
                        id, name, NULL, NULL,
                        purchaseDate, expirationDate, category,
                        NULL, NULL, '', 0, 0
                    FROM warranty_items
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        purchaseId TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        type TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        displayName TEXT,
                        sha256 TEXT,
                        ocrText TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(purchaseId) REFERENCES purchases(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX index_attachments_purchaseId ON attachments(purchaseId)"
                )

                database.execSQL(
                    """
                    INSERT INTO attachments (
                        id, purchaseId, mimeType, type, filePath,
                        displayName, sha256, ocrText, createdAt
                    )
                    SELECT
                        'legacy-' || id,
                        id,
                        'image/jpeg',
                        'RECEIPT',
                        imagePath,
                        'Comprovante legado',
                        NULL,
                        NULL,
                        0
                    FROM warranty_items
                    WHERE imagePath IS NOT NULL
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE warranty_items")
            }
        }
    }
}
