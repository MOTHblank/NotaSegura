package com.mothblank.notasegura.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationPermissionPolicy {
    private const val PREFS = "notification_permission"
    private const val PROMPTED = "prompted_after_save"

    fun shouldRequestAfterSuccessfulSave(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(PROMPTED, false)) return false

        preferences.edit().putBoolean(PROMPTED, true).apply()
        return true
    }
}
