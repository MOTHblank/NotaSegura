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
import java.time.YearMonth
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


    @Test
    fun billingDays29To31RetainEveryMonthlyObligationAcrossLeapAndOrdinaryYears() = runBlocking {
        val start = YearMonth.of(2023, 1)
        val end = YearMonth.of(2025, 4)
        for (anchor in 29..31) {
            db.paymentDao().insertPayment(
                Payment(
                    id = "anchor-$anchor",
                    title = "Billing day $anchor",
                    amountCents = 10_000,
                    dueDate = start.atDay(anchor),
                    recurrenceMonths = 1,
                    recurrenceAnchorDay = anchor
                )
            )
        }

        repository.refreshOccurrences(end.atEndOfMonth())
        repository.refreshOccurrences(end.atEndOfMonth()) // Synchronization must be idempotent.

        for (anchor in 29..31) {
            val expected = (0L..27L).map { offset ->
                val month = start.plusMonths(offset)
                month.atDay(minOf(anchor, month.lengthOfMonth()))
            }
            val actual = db.paymentDao().getPaymentsSnapshot()
                .filter { it.seriesId == "anchor-$anchor" && !it.dueDate.isAfter(end.atEndOfMonth()) }
            assertEquals("One payment for each month, anchored on day $anchor", expected.size, actual.size)
            assertEquals(expected.toSet(), actual.map { it.dueDate }.toSet())
            assertTrue(actual.all { !it.isPaid })
            assertTrue(actual.any { it.dueDate == LocalDate.of(2023, 2, 28) })
            assertTrue(actual.any { it.dueDate == LocalDate.of(2024, 2, 29) })
            assertTrue(actual.any { it.dueDate == LocalDate.of(2025, 2, 28) })
        }
    }

    @Test
    fun movedFebruaryInstallmentDoesNotEraseMarchOrRecreateItsOldSlot() = runBlocking {
        val first = newMonthlyPayment().copy(
            id = "jan-31-series",
            dueDate = LocalDate.of(2024, 1, 31),
            recurrenceAnchorDay = 31
        )
        val end = LocalDate.of(2024, 5, 31)
        db.paymentDao().insertPayment(first)
        repository.refreshOccurrences(end)
        val february = db.paymentDao().getPaymentsSnapshot().single {
            it.seriesId == first.id && it.dueDate == LocalDate.of(2024, 2, 29)
        }

        db.paymentDao().saveWithRecurrence(
            february.copy(dueDate = LocalDate.of(2024, 3, 2), amountCents = 42_000),
            applyToFuture = false,
            horizon = end
        )
        repository.refreshOccurrences(end)

        val rows = db.paymentDao().getPaymentsSnapshot()
            .filter { it.seriesId == first.id && !it.dueDate.isAfter(end) }
        assertEquals(5, rows.size)
        assertFalse(rows.any { it.dueDate == LocalDate.of(2024, 2, 29) })
        assertEquals(42_000L, rows.single { it.id == february.id }.amountCents)
        assertTrue(rows.any { it.dueDate == LocalDate.of(2024, 3, 31) && it.amountCents == 9_900L })
        assertTrue(rows.any { it.dueDate == LocalDate.of(2024, 4, 30) })
        assertTrue(rows.any { it.dueDate == LocalDate.of(2024, 5, 31) })
    }

    @Test
    fun futureSingleInstallmentEditRemainsIndependentFromFollowingMonths() = runBlocking {
        val start = YearMonth.from(LocalDate.now()).plusMonths(1)
        val end = start.plusMonths(5).atEndOfMonth()
        val first = newMonthlyPayment().copy(
            id = "future-edited",
            dueDate = start.atDay(20),
            recurrenceAnchorDay = 20
        )
        db.paymentDao().saveWithRecurrence(first, applyToFuture = false, horizon = end)
        val originalDate = start.plusMonths(2).atDay(20)
        val target = db.paymentDao().getPaymentsSnapshot().single {
            it.seriesId == first.id && it.dueDate == originalDate
        }

        db.paymentDao().saveWithRecurrence(
            target.copy(dueDate = originalDate.plusDays(3), amountCents = 30_000),
            applyToFuture = false,
            horizon = end
        )
        repository.refreshOccurrences(end)

        val rows = db.paymentDao().getPaymentsSnapshot().filter {
            it.seriesId == first.id && !it.dueDate.isAfter(end)
        }
        assertEquals(6, rows.size)
        assertFalse(rows.any { it.dueDate == originalDate })
        assertEquals(30_000L, rows.single { it.id == target.id }.amountCents)
        assertEquals(9_900L, rows.single {
            it.dueDate == start.plusMonths(3).atDay(20)
        }.amountCents)
    }

    @Test
    fun stoppingSeriesAcrossMonthBoundaryPreservesOverdueDebtAndPaidHistory() = runBlocking {
        val first = newMonthlyPayment().copy(
            id = "overdue-series",
            dueDate = LocalDate.of(2024, 1, 31),
            recurrenceAnchorDay = 31
        )
        db.paymentDao().insertPayment(first)
        val end = LocalDate.of(2024, 4, 30)
        repository.refreshOccurrences(end)
        val february = db.paymentDao().getPaymentsSnapshot().single {
            it.seriesId == first.id && it.dueDate == LocalDate.of(2024, 2, 29)
        }
        repository.setPaidStatus(february, true, LocalDate.of(2024, 3, 1))

        db.paymentDao().stopRecurringPlan(first.id, LocalDate.of(2024, 3, 15))
        repository.refreshOccurrences(end)
        val rows = db.paymentDao().getPaymentsSnapshot().filter { it.seriesId == first.id }
        assertFalse(db.paymentDao().getPlansSnapshot().single().isActive)
        assertEquals(2, rows.size)
        assertFalse(rows.single { it.id == first.id }.isPaid)
        assertTrue(rows.single { it.id == february.id }.isPaid)
        assertEquals(listOf(LocalDate.of(2024, 1, 31)), db.paymentDao()
            .getPendingPaymentsDueOnOrBefore(LocalDate.of(2024, 4, 1))
            .map { it.dueDate })
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
