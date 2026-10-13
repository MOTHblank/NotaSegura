package com.mothblank.notasegura

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.repository.PaymentRepositoryImpl
import com.mothblank.notasegura.data.worker.PaymentReminderDelivery
import com.mothblank.notasegura.data.worker.PaymentReminderReconciler
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.util.ReminderDeliveryState
import com.mothblank.notasegura.util.ReminderPreferences
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentReminderReconciliationTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: PaymentRepositoryImpl
    private var priorLeadDays = ReminderPreferences.DEFAULT_PAYMENT_LEAD_DAYS

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        priorLeadDays = ReminderPreferences.paymentLeadDays(context)
        ReminderPreferences.setPaymentLeadDays(context, 3)
        ReminderDeliveryState.clear(context, ReminderDeliveryState.PAYMENT)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = PaymentRepositoryImpl(db.paymentDao())
    }

    @After
    fun cleanup() {
        db.close()
        ReminderDeliveryState.clear(context, ReminderDeliveryState.PAYMENT)
        ReminderPreferences.setPaymentLeadDays(context, priorLeadDays)
    }

    @Test
    fun markingOnePaidReplacesTheSharedNotificationWithTheRemainingObligation() = runBlocking {
        val today = LocalDate.now()
        val overdue = Payment("older-due", "Overdue", 1_000, today.minusDays(6))
        val coming = Payment("upcoming-due", "Soon", 2_000, today.plusDays(2))
        repository.insertPayment(overdue)
        repository.insertPayment(coming)
        val delivery = RecordingDelivery()

        PaymentReminderReconciler.reconcile(context, repository, today, delivery = delivery)
        assertEquals(1, delivery.posts.size)
        assertEquals("notasegura://payments", delivery.posts.single().destination.toString())
        assertEquals(null, delivery.posts.single().markPaidPaymentId)

        // A normal daily run does not post the same alert again.
        PaymentReminderReconciler.reconcile(context, repository, today, delivery = delivery)
        assertEquals(1, delivery.posts.size)

        repository.setPaidStatus(overdue, true, today)
        PaymentReminderReconciler.reconcile(
            context, repository, today, forceRefresh = true, delivery = delivery
        )
        assertEquals(2, delivery.posts.size)
        assertEquals("upcoming-due", delivery.posts.last().markPaidPaymentId)
        assertEquals("notasegura://payment/upcoming-due", delivery.posts.last().destination.toString())
        assertEquals(0, delivery.cancellations)
        assertFalse(db.paymentDao().getPaymentById(coming.id)!!.isPaid)

        // A stale repeated notification action must also republish the remaining bill.
        PaymentReminderReconciler.reconcile(
            context, repository, today, forceRefresh = true, delivery = delivery
        )
        assertEquals(3, delivery.posts.size)

        repository.setPaidStatus(coming, true, today)
        PaymentReminderReconciler.reconcile(
            context, repository, today, forceRefresh = true, delivery = delivery
        )
        assertEquals(1, delivery.cancellations)
        assertEquals(3, delivery.posts.size)
        assertTrue(ReminderDeliveryState.needsDelivery(
            context, ReminderDeliveryState.PAYMENT, "any-old-signature"
        ))
    }

    @Test
    fun failedDeliveryDoesNotCacheTheSignatureAndCanRetry() = runBlocking {
        val today = LocalDate.now()
        repository.insertPayment(Payment("due-today", "Test", 1_000, today))
        val delivery = RecordingDelivery(allowed = false)

        PaymentReminderReconciler.reconcile(context, repository, today, delivery = delivery)
        assertEquals(1, delivery.posts.size)
        delivery.allowed = true
        PaymentReminderReconciler.reconcile(context, repository, today, delivery = delivery)
        assertEquals(2, delivery.posts.size)
        PaymentReminderReconciler.reconcile(context, repository, today, delivery = delivery)
        assertEquals(2, delivery.posts.size)
    }

    private data class Sent(val destination: Uri, val markPaidPaymentId: String?)

    private class RecordingDelivery(var allowed: Boolean = true) : PaymentReminderDelivery {
        val posts = mutableListOf<Sent>()
        var cancellations = 0

        override fun publish(
            message: String,
            destination: Uri,
            actionLabel: String,
            markPaidPaymentId: String?
        ): Boolean {
            posts += Sent(destination, markPaidPaymentId)
            return allowed
        }

        override fun cancel() { cancellations++ }
    }
}
