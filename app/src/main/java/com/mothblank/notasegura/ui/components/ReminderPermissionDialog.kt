package com.mothblank.notasegura.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
        title = { Text("Ativar lembretes?") },
        text = {
            Text(
                "O Nota Segura pode avisar antes do fim de uma garantia ou do vencimento " +
                    "de um pagamento. Você pode mudar essa opção depois nas configurações do celular."
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text("Ativar lembretes")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text("Agora não")
            }
        }
    )
}
