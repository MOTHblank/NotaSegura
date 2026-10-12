package com.mothblank.notasegura.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import com.mothblank.notasegura.domain.model.RecurringPaymentSkip
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY dueDate ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM recurring_payment_plans ORDER BY title COLLATE NOCASE")
    fun getRecurringPlans(): Flow<List<RecurringPaymentPlan>>

    @Query("SELECT * FROM recurring_payment_plans")
    suspend fun getPlansSnapshot(): List<RecurringPaymentPlan>

    @Query("SELECT * FROM recurring_payment_plans WHERE id = :id")
    suspend fun getPlanById(id: String): RecurringPaymentPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: RecurringPaymentPlan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<RecurringPaymentPlan>)

    @Query("DELETE FROM recurring_payment_plans")
    suspend fun clearPlans()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkip(skip: RecurringPaymentSkip)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkips(skips: List<RecurringPaymentSkip>)

    @Query("SELECT * FROM recurring_payment_skips")
    suspend fun getSkipsSnapshot(): List<RecurringPaymentSkip>

    @Query("DELETE FROM recurring_payment_skips")
    suspend fun clearSkips()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentIfAbsent(payment: Payment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<Payment>)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: String): Payment?

    @Query("SELECT * FROM payments")
    suspend fun getPaymentsSnapshot(): List<Payment>

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Query("DELETE FROM payments WHERE seriesId = :seriesId AND dueDate >= :from AND isPaid = 0")
    suspend fun deleteUnpaidSeriesFrom(seriesId: String, from: LocalDate)

    @Query("UPDATE payments SET seriesId = :seriesId WHERE id = :id AND seriesId IS NULL")
    suspend fun linkLegacyRoot(id: String, seriesId: String)

    @Query(
        """SELECT * FROM payments WHERE isPaid = 0 AND dueDate <= :endDate
           ORDER BY dueDate ASC"""
    )
    suspend fun getPendingPaymentsDueOnOrBefore(endDate: LocalDate): List<Payment>

    /**
     * Idempotently recover legacy monthly definitions and materialize every
     * due occurrence through the requested horizon. Paid status never gates
     * generation, so missed months remain visible.
     *
     * Explicit skips are respected and existing paid/edited rows are never
     * overwritten. The horizon and 600-month bound prevent runaway loops.
     */
    @Transaction
    suspend fun synchronizeMonthlyPayments(untilDate: LocalDate) {
        val payments = getPaymentsSnapshot()
        val plans = getPlansSnapshot().associateBy { it.id }.toMutableMap()
        val oldSeries = payments.asSequence()
            .filter { (it.recurrenceMonths ?: 0) > 0 }
            .groupBy { it.seriesId ?: it.id }
        oldSeries.forEach { (id, occurrences) ->
            if (id !in plans) {
                val first = occurrences.minBy { it.dueDate }
                val recent = occurrences.maxBy { it.dueDate }
                val plan = RecurringPaymentPlan(
                    id = id,
                    title = recent.title,
                    amountCents = recent.amountCents,
                    startDate = first.dueDate,
                    anchorDay = first.recurrenceAnchorDay ?: first.dueDate.dayOfMonth
                )
                insertPlan(plan)
                // Older monthly root rows could predate the series ID field.
                occurrences.forEach { if (it.seriesId == null) linkLegacyRoot(it.id, id) }
                plans[id] = plan
            }
        }

        val skipped = getSkipsSnapshot().mapTo(hashSetOf()) { it.seriesId to it.dueDate }
        val existing = payments.mapNotNullTo(hashSetOf()) { occurrence ->
            occurrence.seriesId?.let { it to occurrence.dueDate }
                ?: if ((occurrence.recurrenceMonths ?: 0) > 0) {
                    occurrence.id to occurrence.dueDate
                } else null
        }

        for (plan in plans.values) {
            if (!plan.isActive || plan.startDate.isAfter(untilDate)) continue
            var month = YearMonth.from(plan.startDate)
            val horizon = YearMonth.from(untilDate)
            var monthsVisited = 0
            while (!month.isAfter(horizon) && monthsVisited++ < 600) {
                val due = plan.dueIn(month)
                val key = plan.id to due
                if (!due.isBefore(plan.startDate) && !due.isAfter(untilDate) &&
                    key !in skipped && existing.add(key)
                ) {
                    insertPaymentIfAbsent(
                        Payment(
                            id = UUID.randomUUID().toString(),
                            title = plan.title,
                            amountCents = plan.amountCents,
                            dueDate = due,
                            recurrenceMonths = 1,
                            recurrenceAnchorDay = plan.anchorDay,
                            seriesId = plan.id
                        )
                    )
                }
                month = month.plusMonths(1)
            }
        }
    }

    /**
     * Scope=false edits only this bill; scope=true changes the active plan
     * starting at the selected occurrence. Completed history is retained.
     */
    @Transaction
    suspend fun saveWithRecurrence(payment: Payment, applyToFuture: Boolean, horizon: LocalDate) {
        synchronizeMonthlyPayments(horizon)
        val original = getPaymentById(payment.id)
        val existingPlan = original?.seriesId?.let { getPlanById(it) }

        if (existingPlan == null && payment.recurrenceMonths != null) {
            val id = payment.seriesId ?: payment.id
            insertPlan(
                RecurringPaymentPlan(id, payment.title, payment.amountCents,
                    payment.dueDate, payment.recurrenceAnchorDay ?: payment.dueDate.dayOfMonth)
            )
            insertPayment(payment.copy(seriesId = id))
        } else if (existingPlan != null && applyToFuture) {
            // Future edits change the template without modifying paid history.
            val start = original.dueDate
            // Keep already-overdue debts intact when editing future rules.
            deleteUnpaidSeriesFrom(existingPlan.id, maxOf(start, LocalDate.now()))
            if (payment.recurrenceMonths == null) {
                insertPlan(existingPlan.copy(isActive = false))
                insertPayment(payment.copy(seriesId = null))
            } else {
                insertPlan(
                    existingPlan.copy(
                        title = payment.title,
                        amountCents = payment.amountCents,
                        startDate = maxOf(payment.dueDate, LocalDate.now()),
                        anchorDay = payment.recurrenceAnchorDay ?: payment.dueDate.dayOfMonth,
                        isActive = true
                    )
                )
                insertPayment(payment.copy(seriesId = existingPlan.id))
            }
        } else {
            // When a single occurrence changes date, do not recreate its old
            // scheduled slot on the next reconciliation.
            if (existingPlan != null && original != null &&
                original.dueDate != payment.dueDate
            ) {
                insertSkip(RecurringPaymentSkip(existingPlan.id, original.dueDate))
            }
            insertPayment(payment)
        }
        synchronizeMonthlyPayments(horizon)
    }

    @Transaction
    suspend fun removePayment(payment: Payment, includeFuture: Boolean) {
        synchronizeMonthlyPayments(LocalDate.now().plusMonths(1))
        val plan = payment.seriesId?.let { getPlanById(it) }
        if (plan != null && includeFuture) {
            insertPlan(plan.copy(isActive = false))
            // Ending a subscription must not erase overdue unpaid bills.
            deleteUnpaidSeriesFrom(plan.id, maxOf(payment.dueDate, LocalDate.now()))
            // Paid occurrences, including this one, remain part of the history.
        } else {
            if (plan != null) insertSkip(RecurringPaymentSkip(plan.id, payment.dueDate))
            deletePayment(payment)
        }
    }

    /**
     * Turning a bill back to pending must not remove another month's bill.
     * Those were once generated as children; they are now independent dues.
     */
    @Transaction
    suspend fun updatePaidState(updatedPayment: Payment) {
        insertPayment(updatedPayment)
    }

    @Transaction
    suspend fun stopRecurringPlan(id: String, today: LocalDate) {
        val plan = getPlanById(id) ?: return
        insertPlan(plan.copy(isActive = false))
        deleteUnpaidSeriesFrom(id, today)
    }
}
