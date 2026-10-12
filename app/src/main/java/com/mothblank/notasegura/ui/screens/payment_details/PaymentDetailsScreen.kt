package com.mothblank.notasegura.ui.screens.payment_details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Repeat
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

private val detailDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun PaymentDetailsScreen(
    navController: NavController,
    viewModel: PaymentDetailsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(app.purchaseDocumentStore, app.paymentRepository)
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { navController.popBackStack() }
    }

    if (state.isLoading) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.payment_loading))
        }
    } else {
        val payment = state.payment
        if (payment == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.payment_not_found))
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(stringResource(R.string.payment_back_to_list))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(payment.title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            CurrencyUtils.formatCents(payment.amountCents),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(
                                R.string.payment_due_on,
                                payment.dueDate.format(detailDateFormat)
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val statusText = when {
                            payment.isPaid -> stringResource(R.string.payment_status_paid)
                            payment.dueDate.isBefore(LocalDate.now()) ->
                                stringResource(R.string.payment_status_overdue)
                            else -> stringResource(R.string.payment_status_pending)
                        }
                        Text(statusText, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (state.plan != null) {
                    val plan = checkNotNull(state.plan)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Repeat, contentDescription = null)
                                Text(
                                    stringResource(R.string.payment_recurrence_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                stringResource(R.string.payment_every_month_day, plan.anchorDay),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (plan.isActive) stringResource(R.string.payment_series_active)
                                else stringResource(R.string.payment_series_stopped),
                                style = MaterialTheme.typography.bodySmall
                            )
                            val next = state.series.filter { !it.isPaid && it.id != payment.id }
                                .minByOrNull { it.dueDate }
                            next?.let {
                                Text(
                                    stringResource(
                                        R.string.payment_series_next, it.dueDate.format(detailDateFormat)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    val history = state.series.filter { it.isPaid && it.id != payment.id }
                        .take(4)
                    if (history.isNotEmpty()) {
                        Text(
                            stringResource(R.string.payment_series_history),
                            style = MaterialTheme.typography.titleMedium
                        )
                        history.forEach { past ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    navController.navigate(
                                        AppScreen.PaymentDetails.createRoute(past.id)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(past.dueDate.format(detailDateFormat))
                                    Text(CurrencyUtils.formatCents(past.amountCents))
                                }
                            }
                        }
                    }
                }

                if (!payment.isPaid) {
                    Button(
                        onClick = viewModel::togglePaidStatus,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text(
                            stringResource(R.string.payment_mark_paid),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::togglePaidStatus,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Text(stringResource(R.string.payment_mark_pending))
                    }
                }

                OutlinedButton(
                    onClick = {
                        navController.navigate(AppScreen.AddEditPayment.editRoute(payment.id))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(
                        stringResource(R.string.payment_edit),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (state.plan?.isActive == true) {
                    TextButton(
                        onClick = { confirmStop = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.payment_stop_recurrence))
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        stringResource(R.string.payment_delete_one),
                        Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.payment_delete_one)) },
            text = {
                Text(stringResource(
                    if (state.plan != null) R.string.payment_delete_one_body
                    else R.string.payment_delete_body, state.payment?.title.orEmpty()
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePayment()
                }) { Text(stringResource(R.string.common_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.payment_stop_recurrence)) },
            text = { Text(stringResource(R.string.payment_stop_recurrence_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmStop = false
                    viewModel.deletePayment(includeFuture = true)
                }) { Text(stringResource(R.string.payment_stop_recurrence)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
