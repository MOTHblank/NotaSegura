package com.mothblank.notasegura.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationPermissionPolicy {
    private const val PREFS = "notification_permission"
    private const val OFFER_HANDLED = "offer_handled"

    fun shouldOfferAfterSuccessfulSave(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return !context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(OFFER_HANDLED, false)
    }

    fun markOfferHandled(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(OFFER_HANDLED, true)
            .apply()
    }
}
