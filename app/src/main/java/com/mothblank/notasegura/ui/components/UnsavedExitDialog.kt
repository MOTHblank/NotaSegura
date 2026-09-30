package com.mothblank.notasegura.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mothblank.notasegura.R

@Composable
fun UnsavedExitDialog(
    onKeepEditing: () -> Unit,
    onDiscardAndExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        icon = {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
        },
        title = { Text(stringResource(R.string.unsaved_exit_title)) },
        text = {
            Text(
                stringResource(R.string.unsaved_exit_body)
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.unsaved_exit_keep_editing))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscardAndExit) {
                Text(stringResource(R.string.unsaved_exit_discard))
            }
        }
    )
}
