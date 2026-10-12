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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private val initialDue = LocalDate.now().minusMonths(3).withDayOfMonth(10)
    private val horizon = LocalDate.now().plusMonths(2)

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = PaymentRepositoryImpl(db.paymentDao())
    }

    @After
    fun cleanup() = db.close()

    @Test
    fun generatesEveryMissedMonthWithoutAnyPaidParent() = runBlocking {
        val first = newMonthlyPayment()
        repository.saveEditedPayment(first)
        repository.refreshOccurrences(horizon)

        val months = db.paymentDao().getPaymentsSnapshot().filter { it.seriesId == first.id }
        assertTrue(months.size >= 5)
        assertTrue(months.all { !it.isPaid })
        assertTrue(months.any { it.dueDate == initialDue.plusMonths(1) })
        assertTrue(months.any { it.dueDate == initialDue.plusMonths(2) })
        assertTrue(months.any { it.dueDate == initialDue.plusMonths(3) })
    }

    @Test
    fun editingOneOccurrenceDoesNotRewriteOtherMonthsOrRestoreOldDate() = runBlocking {
        val first = newMonthlyPayment()
        repository.saveEditedPayment(first)
        repository.refreshOccurrences(horizon)
        val target = db.paymentDao().getPaymentsSnapshot()
            .single { it.seriesId == first.id && it.dueDate == initialDue.plusMonths(1) }

        val moved = target.copy(
            amountCents = 50_000,
            dueDate = target.dueDate.plusDays(2)
        )
        repository.saveEditedPayment(moved, applyToFuture = false)
        repository.refreshOccurrences(horizon)

        val rows = db.paymentDao().getPaymentsSnapshot().filter { it.seriesId == first.id }
        assertNull(rows.firstOrNull { it.dueDate == target.dueDate })
        assertEquals(50_000L, rows.single { it.id == target.id }.amountCents)
        assertTrue(rows.any { it.dueDate == initialDue.plusMonths(2) && it.amountCents == 9_900L })
    }

    @Test
    fun futureEditChangesTemplateButPreservesPreviouslyPaidHistory() = runBlocking {
        val first = newMonthlyPayment()
        repository.saveEditedPayment(first)
        repository.setPaidStatus(first, true, LocalDate.now())
        val next = db.paymentDao().getPaymentsSnapshot()
            .single { it.seriesId == first.id && it.dueDate == initialDue.plusMonths(1) }

        repository.saveEditedPayment(next.copy(amountCents = 15_000L), applyToFuture = true)
        repository.refreshOccurrences(horizon)

        val rows = db.paymentDao().getPaymentsSnapshot()
        assertEquals(9_900L, rows.single { it.id == first.id }.amountCents)
        assertTrue(rows.single { it.id == first.id }.isPaid)
        assertTrue(rows.filter {
            it.seriesId == first.id && it.dueDate.isAfter(LocalDate.now())
        }.all { it.amountCents == 15_000L })
        assertTrue(rows.any {
            it.seriesId == first.id && it.dueDate == initialDue.plusMonths(2) &&
                it.amountCents == 9_900L
        })
    }

    @Test
    fun deletedSingleOccurrenceStaysSkippedAndStoppingPreservesPaidMonths() = runBlocking {
        val first = newMonthlyPayment()
        repository.saveEditedPayment(first)
        repository.refreshOccurrences(horizon)
        val skipped = db.paymentDao().getPaymentsSnapshot()
            .single { it.seriesId == first.id && it.dueDate == initialDue.plusMonths(1) }

        repository.removePayment(skipped, includeFuture = false)
        repository.refreshOccurrences(horizon)
        assertNull(db.paymentDao().getPaymentById(skipped.id))
        assertFalse(db.paymentDao().getPaymentsSnapshot().any {
            it.seriesId == first.id && it.dueDate == skipped.dueDate
        })

        repository.setPaidStatus(first, true, LocalDate.now())
        val next = db.paymentDao().getPaymentsSnapshot()
            .single { it.seriesId == first.id && it.dueDate == initialDue.plusMonths(2) }
        repository.removePayment(next, includeFuture = true)
        repository.refreshOccurrences(horizon.plusMonths(6))

        assertFalse(db.paymentDao().getPlansSnapshot().single().isActive)
        assertTrue(db.paymentDao().getPaymentById(first.id)!!.isPaid)
        // Overdue debt is intentionally not erased when a recurrence ends.
        assertNotNull(db.paymentDao().getPaymentById(next.id))
        assertFalse(db.paymentDao().getPaymentsSnapshot().any {
            it.seriesId == first.id && !it.isPaid && it.dueDate.isAfter(horizon)
        })
    }

    @Test
    fun legacyMonthlyRecordsBecomePlansWithoutLosingOriginalRecord() = runBlocking {
        val legacy = newMonthlyPayment().copy(seriesId = "legacy")
        db.paymentDao().insertPayment(legacy)

        repository.refreshOccurrences(horizon)
        assertNotNull(db.paymentDao().getPlanById("legacy"))
        assertNotNull(db.paymentDao().getPaymentById(legacy.id))
        assertTrue(db.paymentDao().getPaymentsSnapshot().any {
            it.seriesId == "legacy" && it.dueDate == initialDue.plusMonths(1)
        })
    }

    private fun newMonthlyPayment() = Payment(
        id = UUID.randomUUID().toString(),
        title = "Internet",
        amountCents = 9_900L,
        dueDate = initialDue,
        recurrenceMonths = 1,
        recurrenceAnchorDay = initialDue.dayOfMonth,
        seriesId = null
    )
}
