package com.mothblank.notasegura.data.local

import androidx.room.*
import com.mothblank.notasegura.domain.model.Payment
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY dueDate ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: String): Payment?

    @Query("SELECT EXISTS(SELECT 1 FROM payments WHERE title = :title AND dueDate = :dueDate LIMIT 1)")
    suspend fun existsPayment(title: String, dueDate: java.time.LocalDate): Boolean
}
