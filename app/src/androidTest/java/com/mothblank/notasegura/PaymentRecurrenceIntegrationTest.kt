package com.mothblank.notasegura

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.repository.PaymentRepositoryImpl
import com.mothblank.notasegura.domain.model.Payment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PaymentRecurrenceIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PaymentRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = PaymentRepositoryImpl(db.paymentDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun editingGeneratedPaymentKeepsParentRelationshipForUndo() = runBlocking {
        val parent = newPayment(isPaid = false)
        repository.saveEditedPayment(parent)
        repository.setPaidStatus(parent, true, LocalDate.of(2026, 10, 12))
        val child = db.paymentDao().getPaymentsSnapshot()
            .single { it.generatedFromId == parent.id }
        repository.saveEditedPayment(child.copy(title = "Parcela alterada"))
        assertEquals(parent.id, repository.getPaymentById(child.id)?.generatedFromId)

        repository.setPaidStatus(
            repository.getPaymentById(parent.id)!!, false, LocalDate.of(2026, 10, 12)
        )
        assertNull(repository.getPaymentById(child.id))
    }

    @Test
    fun changingDueDateOrDisablingRecurrenceReplacesUnpaidChild() = runBlocking {
        val parent = newPayment(isPaid = true)
        repository.saveEditedPayment(parent)
        assertEquals(1, childrenOf(parent.id).size)

        val moved = parent.copy(dueDate = LocalDate.of(2026, 10, 31), recurrenceAnchorDay = 31)
        repository.saveEditedPayment(moved)
        assertEquals(1, childrenOf(parent.id).size)
        assertEquals(LocalDate.of(2026, 11, 30), childrenOf(parent.id).single().dueDate)

        repository.saveEditedPayment(moved.copy(recurrenceMonths = null, seriesId = null))
        assertTrue(childrenOf(parent.id).isEmpty())
    }

    private suspend fun childrenOf(parentId: String) =
        db.paymentDao().getPaymentsSnapshot().filter { it.generatedFromId == parentId }

    private fun newPayment(isPaid: Boolean) = Payment(
        id = UUID.randomUUID().toString(),
        title = "Mensalidade",
        amountCents = 3000L,
        dueDate = LocalDate.of(2026, 10, 12),
        isPaid = isPaid,
        paidAt = if (isPaid) LocalDate.of(2026, 10, 12) else null,
        recurrenceMonths = 1,
        recurrenceAnchorDay = 12,
        seriesId = null
    )
}
