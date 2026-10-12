package com.mothblank.notasegura.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object DatabaseMigrations {
    /**
     * Existing individual payments remain untouched. Plans are reconstructed
     * from their legacy series markers on first synchronization.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SQLiteConnection) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS recurring_payment_plans (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    amountCents INTEGER NOT NULL,
                    startDate INTEGER NOT NULL,
                    anchorDay INTEGER NOT NULL,
                    isActive INTEGER NOT NULL
                )""".trimIndent()
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS recurring_payment_skips (
                    seriesId TEXT NOT NULL,
                    dueDate INTEGER NOT NULL,
                    PRIMARY KEY(seriesId, dueDate)
                )""".trimIndent()
            )
        }
    }
}
