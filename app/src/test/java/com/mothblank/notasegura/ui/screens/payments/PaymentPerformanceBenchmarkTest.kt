package com.mothblank.notasegura.ui.screens.payments

import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureTimeMillis

class FakePaymentRepository(private val payments: List<Payment>) : PaymentRepository {
    var insertCount = 0

    override fun getAllPayments(): Flow<List<Payment>> = flowOf(payments)

    override suspend fun insertPayment(payment: Payment) {
        insertCount++
    }

    override suspend fun deletePayment(payment: Payment) {}
    override suspend fun getPaymentById(id: String): Payment? = null

    // Simulate the new function we will add
    override suspend fun existsPayment(title: String, dueDate: LocalDate): Boolean {
        // In a real DB this would be an EXISTS query which is near instant with an index,
        // or a very fast scan without instantiating objects. Here we simulate it simply.
        return payments.any { it.title == title && it.dueDate == dueDate }
    }
}

class PaymentPerformanceBenchmarkTest {

    @Test
    fun benchmarkCheckIfPaymentExists() = runBlocking {
        // Setup: Create 10,000 payments
        val baseDate = LocalDate.now()
        val payments = (1..10000).map {
            Payment(
                id = UUID.randomUUID().toString(),
                title = "Payment $it",
                amount = 100.0,
                dueDate = baseDate.plusDays((it % 30).toLong()),
                isPaid = false,
                isRecurring = true
            )
        }.toMutableList()

        // Add the specific payment we are looking for at the very end to simulate worst-case
        val targetPayment = Payment(
            id = UUID.randomUUID().toString(),
            title = "Target Payment",
            amount = 100.0,
            dueDate = baseDate.plusMonths(1),
            isPaid = false,
            isRecurring = true
        )
        payments.add(targetPayment)

        val repository = FakePaymentRepository(payments)
        val nextMonthDate = baseDate.plusMonths(1)

        // Original approach: getAllPayments().first() and then .any { ... }
        var result1 = false
        val timeOriginal = measureTimeMillis {
            // we loop 100 times to amplify the measurement
            for (i in 1..100) {
                val allPayments = repository.getAllPayments().first()
                result1 = allPayments.any {
                    it.title == "Target Payment" &&
                    it.dueDate == nextMonthDate
                }
            }
        }

        // Optimized approach
        var result2 = false
        val timeOptimized = measureTimeMillis {
            for (i in 1..100) {
                result2 = repository.existsPayment("Target Payment", nextMonthDate)
            }
        }

        println("BENCHMARK ORIGINAL: $timeOriginal ms")
        println("BENCHMARK OPTIMIZED: $timeOptimized ms")
    }
}
