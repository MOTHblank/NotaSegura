package com.mothblank.notasegura.domain.repository

import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PaymentRepository {
    fun getAllPayments(): Flow<List<Payment>>
    fun getRecurringPlans(): Flow<List<RecurringPaymentPlan>>
    suspend fun refreshOccurrences(untilDate: LocalDate)
    suspend fun saveEditedPayment(payment: Payment, applyToFuture: Boolean = false)
    suspend fun removePayment(payment: Payment, includeFuture: Boolean)
    suspend fun stopRecurringPlan(id: String)
    suspend fun insertPayment(payment: Payment)
    suspend fun deletePayment(payment: Payment)
    suspend fun getPaymentById(id: String): Payment?
    suspend fun setPaidStatus(payment: Payment, isPaid: Boolean, date: LocalDate)
    suspend fun getPendingPaymentsDueOnOrBefore(endDate: LocalDate): List<Payment>
}
