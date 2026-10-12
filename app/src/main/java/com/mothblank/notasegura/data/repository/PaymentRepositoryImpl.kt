package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.PaymentDao
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.nextOccurrenceDate
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class PaymentRepositoryImpl(
    private val dao: PaymentDao
) : PaymentRepository {

    override fun getAllPayments(): Flow<List<Payment>> = dao.getAllPayments()

    override suspend fun insertPayment(payment: Payment) = dao.insertPayment(payment)

    override suspend fun saveEditedPayment(payment: Payment) {
        dao.saveEditedPayment(
            payment,
            if (payment.isPaid) generateNextPayment(payment) else null
        )
    }

    override suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)

    override suspend fun getPaymentById(id: String): Payment? = dao.getPaymentById(id)

    override suspend fun setPaidStatus(payment: Payment, isPaid: Boolean, date: LocalDate) {
        if (!isPaid) {
            dao.updatePaidState(
                payment.copy(isPaid = false, paidAt = null),
                null
            )
            return
        }

        val updated = payment.copy(
            isPaid = true,
            paidAt = payment.paidAt ?: date
        )
        dao.updatePaidState(updated, generateNextPayment(updated))
    }

    private fun generateNextPayment(payment: Payment): Payment? {
        val nextDate = payment.nextOccurrenceDate() ?: return null
        return payment.copy(
            id = UUID.randomUUID().toString(),
            dueDate = nextDate,
            isPaid = false,
            paidAt = null,
            seriesId = payment.seriesId ?: payment.id,
            recurrenceAnchorDay = payment.recurrenceAnchorDay ?: payment.dueDate.dayOfMonth,
            generatedFromId = payment.id
        )
    }

    override suspend fun getPendingPaymentsDueOnOrBefore(
        endDate: LocalDate
    ): List<Payment> = dao.getPendingPaymentsDueOnOrBefore(endDate)
}
