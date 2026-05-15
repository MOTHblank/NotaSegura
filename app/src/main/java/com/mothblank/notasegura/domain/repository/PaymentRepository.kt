package com.mothblank.notasegura.domain.repository

import com.mothblank.notasegura.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getAllPayments(): Flow<List<Payment>>
    suspend fun insertPayment(payment: Payment)
    suspend fun deletePayment(payment: Payment)
    suspend fun getPaymentById(id: String): Payment?
}
