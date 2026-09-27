package com.mothblank.notasegura.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mothblank.notasegura.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY dueDate ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<Payment>)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: String): Payment?

    @Query("SELECT * FROM payments")
    suspend fun getPaymentsSnapshot(): List<Payment>

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM payments
            WHERE seriesId = :seriesId AND dueDate = :dueDate
            LIMIT 1
        )
        """
    )
    suspend fun existsSeriesOccurrence(seriesId: String, dueDate: LocalDate): Boolean

    @Query("DELETE FROM payments WHERE generatedFromId = :sourceId AND isPaid = 0")
    suspend fun deleteUnpaidGeneratedChild(sourceId: String)

    @Query(
        """
        SELECT * FROM payments
        WHERE isPaid = 0
          AND dueDate BETWEEN :startDate AND :endDate
        ORDER BY dueDate ASC
        """
    )
    suspend fun getPendingPaymentsDueBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment>

    @Transaction
    suspend fun updatePaidState(updatedPayment: Payment, nextPayment: Payment?) {
        insertPayment(updatedPayment)

        if (!updatedPayment.isPaid) {
            deleteUnpaidGeneratedChild(updatedPayment.id)
            return
        }

        if (nextPayment != null) {
            val seriesId = nextPayment.seriesId
            if (seriesId == null || !existsSeriesOccurrence(seriesId, nextPayment.dueDate)) {
                insertPayment(nextPayment)
            }
        }
    }
}
