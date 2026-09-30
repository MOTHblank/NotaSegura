package com.mothblank.notasegura.ui.screens.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
fun TimelineScreen(
    navController: NavController,
    viewModel: TimelineViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val purchases by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val attentionSummary by viewModel.attentionSummary.collectAsState()
    val focusManager = LocalFocusManager.current
    var previewAttachment by remember { mutableStateOf<Attachment?>(null) }

    val hasFilters = searchQuery.isNotBlank() || selectedCategory != null

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (
                    attentionSummary.warrantiesExpiringSoon > 0 ||
                    attentionSummary.overduePayments > 0 ||
                    attentionSummary.paymentsDueThisWeek > 0
                ) {
                    AttentionOverview(
                        summary = attentionSummary,
                        onOpenPayments = {
                            navController.navigate(AppScreen.Payments.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pesquisar compras") },
                    placeholder = { Text("Produto, loja ou número de série") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Limpar pesquisa"
                                )
                            }
                        }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (categories.isNotEmpty()) {
                    Text(
                        "Filtrar por categoria",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.onCategorySelected(null) },
                                label = { Text("Todas") }
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { viewModel.onCategorySelected(category) },
                                label = { Text(category) }
                            )
                        }
                    }
                }
            }
        }

        if (purchases.isEmpty()) {
            EmptyPurchaseState(
                filtered = hasFilters,
                onPrimaryAction = {
                    if (hasFilters) {
                        viewModel.onSearchQueryChange("")
                        viewModel.onCategorySelected(null)
                    } else {
                        navController.navigate(AppScreen.AddEditItem.createRoute())
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(purchases, key = { it.purchase.id }) { purchaseItem ->
                    PurchaseCard(
                        item = purchaseItem,
                        onOpen = {
                            navController.navigate(
                                AppScreen.PurchaseDetails.createRoute(purchaseItem.purchase.id)
                            )
                        },
                        onOpenReceipt = {
                            previewAttachment = purchaseItem.attachments.firstOrNull()
                        }
                    )
                }
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
}

@Composable
private fun AttentionOverview(
    summary: AttentionSummary,
    onOpenPayments: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Atenção nos próximos dias",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (summary.warrantiesExpiringSoon > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        if (summary.warrantiesExpiringSoon == 1) {
                            "1 garantia termina nos próximos 30 dias"
                        } else {
                            "${summary.warrantiesExpiringSoon} garantias terminam nos próximos 30 dias"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            val paymentAttention =
                summary.overduePayments + summary.paymentsDueThisWeek
            if (paymentAttention > 0) {
                OutlinedButton(
                    onClick = onOpenPayments,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null)
                    Text(
                        buildString {
                            if (summary.overduePayments > 0) {
                                append(
                                    if (summary.overduePayments == 1) {
                                        "1 pagamento atrasado"
                                    } else {
                                        "${summary.overduePayments} pagamentos atrasados"
                                    }
                                )
                            }
                            if (
                                summary.overduePayments > 0 &&
                                summary.paymentsDueThisWeek > 0
                            ) {
                                append(" • ")
                            }
                            if (summary.paymentsDueThisWeek > 0) {
                                append(
                                    if (summary.paymentsDueThisWeek == 1) {
                                        "1 vence nesta semana"
                                    } else {
                                        "${summary.paymentsDueThisWeek} vencem nesta semana"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPurchaseState(
    filtered: Boolean,
    onPrimaryAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (filtered) {
                    "Nenhuma compra encontrada"
                } else {
                    "Você ainda não cadastrou nenhuma compra"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (filtered) {
                    "Tente limpar a pesquisa ou os filtros."
                } else {
                    "Cadastre uma compra para guardar comprovantes e acompanhar a garantia."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    if (filtered) "Limpar pesquisa e filtros" else "Adicionar primeira compra"
                )
            }
        }
    }
}

@Composable
private fun PurchaseCard(
    item: PurchaseWithAttachments,
    onOpen: () -> Unit,
    onOpenReceipt: () -> Unit
) {
    val purchase = item.purchase
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                purchase.productName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            purchase.merchant?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Comprado em $it",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            purchase.purchaseValueCents?.let {
                Text(
                    CurrencyUtils.formatCents(it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Data da compra: ${purchase.purchaseDate.format(formatter)}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (purchase.category.isNotBlank()) {
                Text(
                    "Categoria: ${purchase.category}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.attachments.isNotEmpty()) {
                Text(
                    if (item.attachments.size == 1) {
                        "1 documento guardado"
                    } else {
                        "${item.attachments.size} documentos guardados"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            WarrantyStatus(purchase.warrantyEndDate, formatter)

            if (item.attachments.isNotEmpty()) {
                Button(
                    onClick = onOpenReceipt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Text(
                        "Abrir comprovante",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("Ver detalhes")
            }
        }
    }
}

@Composable
private fun WarrantyStatus(
    warrantyEndDate: LocalDate?,
    formatter: DateTimeFormatter
) {
    val today = LocalDate.now()

    val containerColor: androidx.compose.ui.graphics.Color
    val contentColor: androidx.compose.ui.graphics.Color
    val title: String
    val detail: String

    if (warrantyEndDate == null) {
        containerColor = MaterialTheme.colorScheme.surfaceVariant
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        title = "Garantia não informada"
        detail = "Você pode adicionar essa data ao editar a compra."
    } else {
        val days = ChronoUnit.DAYS.between(today, warrantyEndDate)
        when {
            days < 0 -> {
                containerColor = MaterialTheme.colorScheme.errorContainer
                contentColor = MaterialTheme.colorScheme.onErrorContainer
                title = "Garantia encerrada"
                detail = "Terminou em ${warrantyEndDate.format(formatter)}."
            }

            days <= 30 -> {
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                title = "Garantia perto do fim"
                detail = "Termina em ${warrantyEndDate.format(formatter)}."
            }

            else -> {
                containerColor = MaterialTheme.colorScheme.primaryContainer
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                title = "Garantia ativa"
                detail = "Até ${warrantyEndDate.format(formatter)}."
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}
