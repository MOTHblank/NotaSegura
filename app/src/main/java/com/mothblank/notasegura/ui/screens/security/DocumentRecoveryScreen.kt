package com.mothblank.notasegura.ui.screens.security

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.R
import com.mothblank.notasegura.data.storage.QuarantinedDocument
import com.mothblank.notasegura.data.storage.QuarantinedDocumentStore
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.ui.components.DocumentViewerDialog
import com.mothblank.notasegura.util.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocumentRecoveryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NotaSeguraApplication
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf<List<QuarantinedDocument>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var selectedForRestore by remember { mutableStateOf<QuarantinedDocument?>(null) }
    var selectedForPreview by remember { mutableStateOf<QuarantinedDocument?>(null) }
    var selectedForExport by remember { mutableStateOf<QuarantinedDocument?>(null) }

    suspend fun refresh() {
        documents = QuarantinedDocumentStore.list(context)
        purchases = withContext(Dispatchers.IO) {
            app.database.purchaseDao().getPurchasesSnapshot()
        }.sortedBy { it.productName.lowercase() }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { response ->
        val document = selectedForExport
        selectedForExport = null
        val uri = response.data?.data
        if (response.resultCode == Activity.RESULT_OK && uri != null && document != null) {
            scope.launch {
                val result = QuarantinedDocumentStore.export(context, document.key, uri)
                Toast.makeText(
                    context,
                    if (result.isSuccess) context.getString(R.string.recovery_export_success)
                    else result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.recovery_export_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.recovery_intro),
            style = MaterialTheme.typography.bodyMedium
        )
        if (loading) {
            Text(stringResource(R.string.recovery_loading))
        } else if (documents.isEmpty()) {
            Text(stringResource(R.string.recovery_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(documents, key = { it.key }) { document ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(document.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.recovery_size_kb, (document.sizeBytes + 1023) / 1024),
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = { selectedForPreview = document },
                                enabled = !busy && document.mimeType != "application/octet-stream"
                            ) {
                                Text(stringResource(R.string.recovery_preview))
                            }
                            Button(
                                onClick = { selectedForRestore = document },
                                enabled = !busy && purchases.isNotEmpty() &&
                                    document.mimeType != "application/octet-stream" &&
                                    document.sizeBytes in 1..FileStorageManager.MAX_ATTACHMENT_BYTES
                            ) {
                                Text(stringResource(R.string.recovery_restore))
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedForExport = document
                                    exportLauncher.launch(
                                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = document.mimeType
                                            putExtra(Intent.EXTRA_TITLE, document.displayName)
                                        }
                                    )
                                },
                                enabled = !busy
                            ) {
                                Text(stringResource(R.string.recovery_export))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedForPreview?.let { document ->
        DocumentViewerDialog(
            path = document.path,
            mimeType = document.mimeType,
            displayName = document.displayName,
            onDismiss = { selectedForPreview = null }
        )
    }

    selectedForRestore?.let { document ->
        AlertDialog(
            onDismissRequest = { if (!busy) selectedForRestore = null },
            title = { Text(stringResource(R.string.recovery_choose_purchase)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(purchases, key = { it.id }) { purchase ->
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    val result = QuarantinedDocumentStore.restore(
                                        context, app.database, document.key, purchase.id
                                    )
                                    Toast.makeText(
                                        context,
                                        if (result.isSuccess) context.getString(R.string.recovery_restored)
                                        else result.exceptionOrNull()?.localizedMessage
                                            ?: context.getString(R.string.recovery_restore_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    selectedForRestore = null
                                    busy = false
                                    refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) {
                            Text(purchase.productName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { selectedForRestore = null },
                    enabled = !busy
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
