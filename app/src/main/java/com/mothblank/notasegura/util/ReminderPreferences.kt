package com.mothblank.notasegura.util

import android.content.Context

object ReminderPreferences {
    const val DEFAULT_WARRANTY_LEAD_DAYS = 30
    const val DEFAULT_PAYMENT_LEAD_DAYS = 3

    val warrantyLeadDayOptions = listOf(0, 7, 14, 30, 60)
    val paymentLeadDayOptions = listOf(0, 1, 3, 7, 14)

    private const val PREFERENCES_NAME = "reminder_preferences"
    private const val KEY_WARRANTY_LEAD_DAYS = "warranty_lead_days"
    private const val KEY_PAYMENT_LEAD_DAYS = "payment_lead_days"

    fun warrantyLeadDays(context: Context): Int =
        preferences(context).getInt(
            KEY_WARRANTY_LEAD_DAYS,
            DEFAULT_WARRANTY_LEAD_DAYS
        ).takeIf { it in warrantyLeadDayOptions }
            ?: DEFAULT_WARRANTY_LEAD_DAYS

    fun paymentLeadDays(context: Context): Int =
        preferences(context).getInt(
            KEY_PAYMENT_LEAD_DAYS,
            DEFAULT_PAYMENT_LEAD_DAYS
        ).takeIf { it in paymentLeadDayOptions }
            ?: DEFAULT_PAYMENT_LEAD_DAYS

    fun setWarrantyLeadDays(context: Context, days: Int) {
        require(days in warrantyLeadDayOptions)
        preferences(context).edit().putInt(KEY_WARRANTY_LEAD_DAYS, days).apply()
    }

    fun setPaymentLeadDays(context: Context, days: Int) {
        require(days in paymentLeadDayOptions)
        preferences(context).edit().putInt(KEY_PAYMENT_LEAD_DAYS, days).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
