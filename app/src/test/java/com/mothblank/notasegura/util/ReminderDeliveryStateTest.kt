package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderDeliveryStateTest {
    @Test
    fun signatureIsStableAcrossOrderingButNotChanges() {
        val before = ReminderDeliveryState.signature(
            30, listOf("purchase-a:2026-10-20:30", "purchase-b:2026-10-23:30")
        )
        val reversed = ReminderDeliveryState.signature(
            30, listOf("purchase-b:2026-10-23:30", "purchase-a:2026-10-20:30")
        )
        assertEquals(before, reversed)
        assertNotEquals(
            before,
            ReminderDeliveryState.signature(7, listOf("purchase-b:2026-10-23:30", "purchase-a:2026-10-20:30"))
        )
        assertNotEquals(
            before,
            ReminderDeliveryState.signature(30, listOf("purchase-a:2026-10-20:7", "purchase-b:2026-10-23:30"))
        )
    }

    @Test
    fun urgencyChangesAtMeaningfulThresholdsRatherThanDaily() {
        assertEquals(60, ReminderDeliveryState.urgencyBucket(45))
        assertEquals(30, ReminderDeliveryState.urgencyBucket(30))
        assertEquals(30, ReminderDeliveryState.urgencyBucket(8))
        assertEquals(7, ReminderDeliveryState.urgencyBucket(7))
        assertEquals(1, ReminderDeliveryState.urgencyBucket(1))
        assertEquals(0, ReminderDeliveryState.urgencyBucket(0))
        assertEquals(-1, ReminderDeliveryState.urgencyBucket(-1))
        assertEquals(-1, ReminderDeliveryState.urgencyBucket(-90))
    }
}
