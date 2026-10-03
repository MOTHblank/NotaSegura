package com.mothblank.notasegura.ui.screens.timeline

import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimelineDashboardTest {

    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun dashboardIncludesOnlyRelevantUpcomingDeadlines() {
        val purchases = listOf(
            purchase("soon", "Notebook", today.plusDays(12)),
            purchase("today", "Fone", today),
            purchase("expired", "Mouse", today.minusDays(1)),
            purchase("later", "Monitor", today.plusDays(31)),
            purchase("none", "Cabo", null)
        )
        val payments = listOf(
            payment("overdue", "Internet", today.minusDays(2)),
            payment("today", "Energia", today),
            payment("week", "Condomínio", today.plusDays(7)),
            payment("later", "Seguro", today.plusDays(8)),
            payment("paid", "Telefone", today.plusDays(1), isPaid = true)
        )

        val dashboard = buildAttentionDashboard(purchases, payments, today)

        assertEquals(
            listOf("today", "soon"),
            dashboard.warrantiesExpiringSoon.map { it.purchaseId }
        )
        assertEquals(
            listOf("overdue", "today", "week"),
            dashboard.paymentsRequiringAttention.map { it.paymentId }
        )
        assertEquals(1, dashboard.overduePayments)
        assertEquals(2, dashboard.paymentsDueThisWeek)
        assertFalse(dashboard.isEmpty)
    }

    @Test
    fun emptyDashboardWhenNothingFallsInsideAttentionWindows() {
        val dashboard = buildAttentionDashboard(
            purchases = listOf(purchase("later", "TV", today.plusDays(90))),
            payments = listOf(payment("later", "IPTU", today.plusDays(20))),
            today = today
        )

        assertTrue(dashboard.isEmpty)
        assertEquals(0, dashboard.overduePayments)
        assertEquals(0, dashboard.paymentsDueThisWeek)
    }

    private fun purchase(
        id: String,
        productName: String,
        warrantyEndDate: LocalDate?
    ) = PurchaseWithAttachments(
        purchase = Purchase(
            id = id,
            productName = productName,
            purchaseDate = today.minusMonths(2),
            warrantyEndDate = warrantyEndDate,
            createdAt = 0L,
            updatedAt = 0L
        ),
        attachments = emptyList()
    )

    private fun payment(
        id: String,
        title: String,
        dueDate: LocalDate,
        isPaid: Boolean = false
    ) = Payment(
        id = id,
        title = title,
        amountCents = 12_345L,
        dueDate = dueDate,
        isPaid = isPaid
    )
}
