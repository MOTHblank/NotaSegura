package com.mothblank.notasegura.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
        title = { Text("Sair sem salvar?") },
        text = {
            Text(
                "As alterações feitas nesta tela serão perdidas."
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Continuar editando")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscardAndExit) {
                Text("Sair sem salvar")
            }
        }
    )
}
