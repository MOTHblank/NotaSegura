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
        val nextDate = updated.nextOccurrenceDate()
        val nextPayment = if (nextDate != null) {
            val seriesId = updated.seriesId ?: updated.id
            updated.copy(
                id = UUID.randomUUID().toString(),
                dueDate = nextDate,
                isPaid = false,
                paidAt = null,
                seriesId = seriesId,
                recurrenceAnchorDay = updated.recurrenceAnchorDay ?: updated.dueDate.dayOfMonth,
                generatedFromId = updated.id
            )
        } else {
            null
        }

        dao.updatePaidState(updated, nextPayment)
    }

    override suspend fun getPendingPaymentsDueOnOrBefore(
        endDate: LocalDate
    ): List<Payment> = dao.getPendingPaymentsDueOnOrBefore(endDate)
}
