package com.mothblank.notasegura.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mothblank.notasegura.R

@Composable
fun CreateEncryptedBackupDialog(
    minPasswordLength: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PasswordValidationError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_password_body))
                Text(
                    stringResource(R.string.backup_password_warning),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error == PasswordValidationError.TOO_SHORT
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error == PasswordValidationError.MISMATCH
                )
                when (error) {
                    PasswordValidationError.TOO_SHORT -> Text(
                        stringResource(R.string.backup_password_too_short, minPasswordLength),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    PasswordValidationError.MISMATCH -> Text(
                        stringResource(R.string.backup_password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    null -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.length < minPasswordLength -> PasswordValidationError.TOO_SHORT
                        password != confirmation -> PasswordValidationError.MISMATCH
                        else -> null
                    }
                    if (error == null) onConfirm(password)
                }
            ) {
                Text(stringResource(R.string.backup_encrypt_and_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun RestoreEncryptedBackupDialog(
    minPasswordLength: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var tooShort by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_restore_body))
                Text(stringResource(R.string.backup_restore_password_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        tooShort = false
                    },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = tooShort
                )
                if (tooShort) {
                    Text(
                        stringResource(R.string.backup_password_too_short, minPasswordLength),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    tooShort = password.length < minPasswordLength
                    if (!tooShort) onConfirm(password)
                }
            ) {
                Text(stringResource(R.string.common_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private enum class PasswordValidationError {
    TOO_SHORT,
    MISMATCH
}
