package com.mothblank.notasegura.util

import android.content.Context
import java.security.MessageDigest

/**
 * A notification is sent when its records or urgency milestone change,
 * rather than repeating an identical alert on every daily worker run.
 */
object ReminderDeliveryState {
    const val WARRANTY = "warranty"
    const val PAYMENT = "payment"
    private const val PREFS = "reminder_delivery_state"

    fun signature(leadDays: Int, entries: List<String>): String {
        val normalized = listOf(leadDays.toString()).plus(entries.sorted())
            .joinToString(separator = "\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun urgencyBucket(daysUntilDue: Long): Int = when {
        daysUntilDue < 0L -> -1
        daysUntilDue == 0L -> 0
        daysUntilDue == 1L -> 1
        daysUntilDue <= 7L -> 7
        daysUntilDue <= 30L -> 30
        else -> 60
    }

    fun needsDelivery(context: Context, category: String, signature: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(category, null) != signature

    fun delivered(context: Context, category: String, signature: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(category, signature).apply()
    }

    fun clear(context: Context, category: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(category).apply()
    }
}
