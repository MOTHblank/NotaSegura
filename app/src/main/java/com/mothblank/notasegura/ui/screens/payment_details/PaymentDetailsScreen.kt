package com.mothblank.notasegura.ui.screens.payment_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.R
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.util.CurrencyUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun PaymentDetailsScreen(
    navController: NavController,
    viewModel: PaymentDetailsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect {
            navController.popBackStack()
        }
    }

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.payment_loading))
            }
        }

        uiState.payment == null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.payment_not_found),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.payment_back_to_list))
                }
            }
        }

        else -> {
            PaymentDetailsContent(
                payment = checkNotNull(uiState.payment),
                onTogglePaid = viewModel::togglePaidStatus,
                onEdit = {
                    navController.navigate(
                        AppScreen.AddEditPayment.editRoute(checkNotNull(uiState.payment).id)
                    )
                },
                onDelete = { showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog) {
        val title = uiState.payment?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.payment_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.payment_delete_body, title)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePayment()
                    }
                ) {
                    Text(stringResource(R.string.common_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun PaymentDetailsContent(
    payment: Payment,
    onTogglePaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                    payment.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    CurrencyUtils.formatCents(payment.amountCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        R.string.payment_due_on,
                        payment.dueDate.format(formatter)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        PaymentDetailsStatus(payment, formatter)

        if (payment.recurrenceMonths != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    stringResource(R.string.payment_monthly_explanation),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge
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
                Text(stringResource(R.string.payment_mark_pending))
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
                    stringResource(R.string.payment_mark_paid),
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
                stringResource(R.string.payment_edit),
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
                stringResource(R.string.payment_delete),
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PaymentDetailsStatus(
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
            title = stringResource(R.string.payment_status_paid)
            detail = payment.paidAt?.let {
                stringResource(R.string.payment_paid_on, it.format(formatter))
            } ?: stringResource(R.string.payment_completed)
        }
        daysUntilDue < 0 -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Default.WarningAmber
            title = stringResource(R.string.payment_status_overdue)
            detail = stringResource(
                R.string.payment_was_due_on,
                payment.dueDate.format(formatter)
            )
        }
        daysUntilDue <= 7 -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Default.WarningAmber
            title = stringResource(R.string.payment_status_due_soon)
            detail = stringResource(
                R.string.payment_due_on,
                payment.dueDate.format(formatter)
            )
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            icon = Icons.Default.CheckCircle
            title = stringResource(R.string.payment_status_pending)
            detail = "Vencimento em ${payment.dueDate.format(formatter)}."
        }
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
            Icon(icon, contentDescription = null, tint = contentColor)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
        }
    }
}
