package com.mothblank.notasegura.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mothblank.notasegura.R

@Composable
fun ReminderPermissionDialog(
    onEnable: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.reminder_permission_title)) },
        text = {
            Text(
                stringResource(R.string.reminder_permission_body)
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.reminder_permission_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.common_not_now))
            }
        }
    )
}
