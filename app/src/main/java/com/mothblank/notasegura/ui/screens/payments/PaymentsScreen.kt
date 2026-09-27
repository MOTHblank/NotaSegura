package com.mothblank.notasegura.ui.screens.payments

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.util.CurrencyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val payments by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showOnlyPending by viewModel.showOnlyPending.collectAsState()
    val focusManager = LocalFocusManager.current
    var pendingDelete by remember { mutableStateOf<Payment?>(null) }

    val hasFilters = searchQuery.isNotBlank() || showOnlyPending

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pesquisar pagamentos") },
                    placeholder = { Text("Nome da conta ou cobrança") },
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

                FilterChip(
                    selected = showOnlyPending,
                    onClick = viewModel::onToggleShowOnlyPending,
                    label = { Text("Mostrar só pendentes") },
                    leadingIcon = if (showOnlyPending) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }

        if (payments.isEmpty()) {
            EmptyPaymentState(
                filtered = hasFilters,
                onPrimaryAction = {
                    if (hasFilters) {
                        viewModel.onSearchQueryChange("")
                        if (showOnlyPending) {
                            viewModel.onToggleShowOnlyPending()
                        }
                    } else {
                        navController.navigate(AppScreen.AddEditPayment.createRoute())
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
                items(payments, key = { it.id }) { payment ->
                    PaymentCard(
                        payment = payment,
                        onTogglePaid = { viewModel.togglePaidStatus(payment) },
                        onEdit = {
                            navController.navigate(
                                AppScreen.AddEditPayment.editRoute(payment.id)
                            )
                        },
                        onDelete = { pendingDelete = payment }
                    )
                }
            }
        }
    }

    pendingDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null)
            },
            title = { Text("Excluir este pagamento?") },
            text = {
                Text(
                    "“${payment.title}” será apagado deste aparelho. " +
                        "Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(payment)
                        pendingDelete = null
                    }
                ) {
                    Text("Sim, excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EmptyPaymentState(
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
                Icons.Default.Payments,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                if (filtered) {
                    "Nenhum pagamento encontrado"
                } else {
                    "Você ainda não cadastrou nenhum pagamento"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                if (filtered) {
                    "Tente limpar a pesquisa ou mostrar todos os pagamentos."
                } else {
                    "Cadastre contas e cobranças para acompanhar as datas de vencimento."
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
                    if (filtered) "Limpar pesquisa e filtros" else "Adicionar primeiro pagamento"
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: Payment,
    onTogglePaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (payment.isPaid) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                payment.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                CurrencyUtils.formatCents(payment.amountCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            PaymentStatus(payment, formatter)

            if (payment.recurrenceMonths != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "Pagamento mensal. Ao marcar como pago, o próximo mês é criado automaticamente.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (payment.isPaid) {
                OutlinedButton(
                    onClick = onTogglePaid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Marcar como pendente")
                }
            } else {
                Button(
                    onClick = onTogglePaid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text(
                        "Marcar como pago",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text(
                    "Editar pagamento",
                    modifier = Modifier.padding(start = 8.dp)
                )
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
                    "Excluir pagamento",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PaymentStatus(
    payment: Payment,
    formatter: DateTimeFormatter
) {
    val today = LocalDate.now()
    val daysUntilDue = ChronoUnit.DAYS.between(today, payment.dueDate)

    val containerColor: androidx.compose.ui.graphics.Color
    val contentColor: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val title: String
    val detail: String

    when {
        payment.isPaid -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Default.CheckCircle
            title = "Pago"
            detail = payment.paidAt?.let {
                "Pago em ${it.format(formatter)}."
            } ?: "Pagamento concluído."
        }

        daysUntilDue < 0 -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Default.WarningAmber
            title = "Pagamento atrasado"
            detail = "Venceu em ${payment.dueDate.format(formatter)}."
        }

        daysUntilDue <= 7 -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Default.WarningAmber
            title = "Vence em breve"
            detail = "Vencimento em ${payment.dueDate.format(formatter)}."
        }

        else -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            icon = Icons.Default.Event
            title = "Próximo vencimento"
            detail = payment.dueDate.format(formatter)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
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
}
