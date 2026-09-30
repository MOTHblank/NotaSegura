package com.mothblank.notasegura.ui.screens.purchase_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.ui.components.DocumentViewerDialog
import com.mothblank.notasegura.util.CurrencyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailsScreen(
    navController: NavController,
    viewModel: PurchaseDetailsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewAttachment by remember { mutableStateOf<Attachment?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes da compra") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Carregando compra...")
                }
            }

            uiState.item == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Esta compra não foi encontrada.",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Voltar para compras")
                    }
                }
            }

            else -> {
                PurchaseDetailsContent(
                    item = checkNotNull(uiState.item),
                    onOpenDocument = { previewAttachment = it },
                    onEdit = {
                        navController.navigate(
                            AppScreen.AddEditItem.editRoute(checkNotNull(uiState.item).purchase.id)
                        )
                    },
                    onDelete = { showDeleteDialog = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    previewAttachment?.let { attachment ->
        DocumentViewerDialog(
            path = attachment.filePath,
            mimeType = attachment.mimeType,
            displayName = attachment.displayName ?: "Documento",
            onDismiss = { previewAttachment = null }
        )
    }

    if (showDeleteDialog) {
        val productName = uiState.item?.purchase?.productName.orEmpty()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Excluir esta compra?") },
            text = {
                Text(
                    "“$productName” e todos os documentos anexados serão apagados deste aparelho. " +
                        "Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePurchase()
                    }
                ) {
                    Text("Sim, excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PurchaseDetailsContent(
    item: PurchaseWithAttachments,
    onOpenDocument: (Attachment) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purchase = item.purchase
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    purchase.productName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                purchase.merchant?.takeIf { it.isNotBlank() }?.let {
                    Text("Comprado em $it", style = MaterialTheme.typography.bodyLarge)
                }
                purchase.purchaseValueCents?.let {
                    Text(
                        CurrencyUtils.formatCents(it),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Compra em ${purchase.purchaseDate.format(formatter)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (item.attachments.isNotEmpty()) {
            DetailsSection(
                title = if (item.attachments.size == 1) "Comprovante / documento" else "Comprovantes / documentos"
            ) {
                item.attachments.forEach { attachment ->
                    OutlinedButton(
                        onClick = { onOpenDocument(attachment) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Icon(
                            if (attachment.mimeType == "application/pdf") {
                                Icons.Default.PictureAsPdf
                            } else {
                                Icons.Default.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            attachment.displayName ?: "Abrir documento",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        DetailsSection(title = "Garantia") {
            WarrantyDetails(purchase.warrantyEndDate, formatter)
        }

        val hasExtraDetails =
            purchase.category.isNotBlank() ||
                !purchase.modelNumber.isNullOrBlank() ||
                !purchase.serialNumber.isNullOrBlank() ||
                purchase.notes.isNotBlank()

        if (hasExtraDetails) {
            DetailsSection(title = "Mais informações") {
                purchase.category.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Categoria", it)
                }
                purchase.modelNumber?.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Modelo", it)
                }
                purchase.serialNumber?.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Nº de série", it)
                }
                purchase.notes.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Observações", it)
                }
            }
        }

        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Text("Editar compra", modifier = Modifier.padding(start = 8.dp))
        }

        TextButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                "Excluir compra",
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DetailsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WarrantyDetails(
    warrantyEndDate: LocalDate?,
    formatter: DateTimeFormatter
) {
    if (warrantyEndDate == null) {
        Text(
            "Nenhuma garantia registrada.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val today = LocalDate.now()
    val daysRemaining = ChronoUnit.DAYS.between(today, warrantyEndDate)
    val (containerColor, contentColor, title) = when {
        daysRemaining < 0 -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Garantia encerrada"
        )
        daysRemaining <= 30 -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Garantia termina em breve"
        )
        else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Garantia ativa"
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(
                "Até ${warrantyEndDate.format(formatter)}",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
    }
}
