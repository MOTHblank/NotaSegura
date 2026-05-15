package com.mothblank.notasegura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.WarrantyItem

@Database(
    entities = [WarrantyItem::class, Payment::class], // Lista de todas as entidades (tabelas)
    version = 2 // Incrementar a versão ao fazer mudanças no schema
)
@TypeConverters(Converters::class) // Registra nosso conversor de datas
abstract class AppDatabase : RoomDatabase() {

    abstract fun warrantyItemDao(): WarrantyItemDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Adiciona a coluna imagePath na tabela warranty_items
                database.execSQL("ALTER TABLE warranty_items ADD COLUMN imagePath TEXT")
                
                // Cria a tabela payments
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        dueDate INTEGER NOT NULL,
                        isPaid INTEGER NOT NULL,
                        isRecurring INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}


