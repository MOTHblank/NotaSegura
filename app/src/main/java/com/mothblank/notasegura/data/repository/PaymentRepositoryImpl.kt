package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.PaymentDao
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PaymentRepositoryImpl(private val dao: PaymentDao) : PaymentRepository {
    override fun getAllPayments(): Flow<List<Payment>> = dao.getAllPayments()
    override fun getRecurringPlans(): Flow<List<RecurringPaymentPlan>> = dao.getRecurringPlans()

    override suspend fun insertPayment(payment: Payment) = dao.insertPayment(payment)

    override suspend fun refreshOccurrences(untilDate: LocalDate) =
        dao.synchronizeMonthlyPayments(untilDate)

    override suspend fun saveEditedPayment(payment: Payment, applyToFuture: Boolean) =
        dao.saveWithRecurrence(payment, applyToFuture, LocalDate.now().plusMonths(1))

    override suspend fun removePayment(payment: Payment, includeFuture: Boolean) =
        dao.removePayment(payment, includeFuture)

    override suspend fun stopRecurringPlan(id: String) =
        dao.stopRecurringPlan(id, LocalDate.now())

    override suspend fun deletePayment(payment: Payment) = dao.removePayment(payment, false)

    override suspend fun getPaymentById(id: String): Payment? = dao.getPaymentById(id)

    override suspend fun setPaidStatus(payment: Payment, isPaid: Boolean, date: LocalDate) {
        // Use the persisted record so a stale card or notification cannot erase
        // the series ID and other changes made since it was rendered.
        val current = dao.getPaymentById(payment.id) ?: return
        dao.updatePaidState(
            current.copy(isPaid = isPaid, paidAt = if (isPaid) current.paidAt ?: date else null)
        )
        refreshOccurrences(LocalDate.now().plusMonths(1))
    }

    override suspend fun getPendingPaymentsDueOnOrBefore(endDate: LocalDate): List<Payment> =
        dao.getPendingPaymentsDueOnOrBefore(endDate)
}
