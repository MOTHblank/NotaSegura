package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.PaymentDao
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow

class PaymentRepositoryImpl(
    private val dao: PaymentDao
) : PaymentRepository {
    override fun getAllPayments(): Flow<List<Payment>> = dao.getAllPayments()
    override suspend fun insertPayment(payment: Payment) = dao.insertPayment(payment)
    override suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)
    override suspend fun getPaymentById(id: String): Payment? = dao.getPaymentById(id)
    override suspend fun existsPayment(title: String, dueDate: java.time.LocalDate): Boolean = dao.existsPayment(title, dueDate)
}
