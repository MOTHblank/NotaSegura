package com.mothblank.notasegura.ui.screens.payments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.navigation.AppScreen
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel(
        factory = ViewModelFactory(
            repository = (LocalContext.current.applicationContext as NotaSeguraApplication).repository,
            paymentRepository = (LocalContext.current.applicationContext as NotaSeguraApplication).paymentRepository
        )
    )
) {
    val payments by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showOnlyPending by viewModel.showOnlyPending.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Filter Header
        Surface(
            tonalElevation = 4.dp,
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
                    placeholder = { Text("Pesquisar pagamentos...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar pesquisa")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                FilterChip(
                    selected = showOnlyPending,
                    onClick = { viewModel.onToggleShowOnlyPending() },
                    label = { Text("Apenas pendentes") },
                    leadingIcon = if (showOnlyPending) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }

        if (payments.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Nenhum pagamento cadastrado",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(payments, key = { it.id }) { payment ->
                    var isVisible by remember(payment.id) { mutableStateOf(true) }

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                isVisible = false
                                true
                            } else {
                                false
                            }
                        }
                    )

                    LaunchedEffect(isVisible, payment.id) {
                        if (!isVisible) {
                            delay(300L)
                            viewModel.deletePayment(payment)
                        }
                    }

                    AnimatedVisibility(
                        visible = isVisible,
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 300)) +
                                fadeOut(animationSpec = tween(durationMillis = 300))
                    ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                DismissBackground(dismissState = dismissState)
                            },
                            content = {
                                PaymentCard(
                                    payment = payment,
                                    onTogglePaid = { viewModel.togglePaidStatus(payment) },
                                    onClick = {
                                        navController.navigate(
                                            AppScreen.AddEditPayment.editRoute(
                                                payment.id
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DismissBackground(dismissState: SwipeToDismissBoxState) {
        val color = when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        }

        val alignment = when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            SwipeToDismissBoxValue.Settled -> Alignment.Center
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color)
                .padding(horizontal = 20.dp),
            contentAlignment = alignment
        ) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Deletar Pagamento",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    @Composable
    fun PaymentCard(
        payment: Payment,
        onTogglePaid: () -> Unit,
        onClick: () -> Unit
    ) {
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (payment.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.4f
                )
                else MaterialTheme.colorScheme.surface,
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                IconButton(
                    onClick = onTogglePaid,
                    modifier = Modifier.size(48.dp) // Larger touch target
                ) {
                    Icon(
                        imageVector = if (payment.isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (payment.isPaid) "Desmarcar como pago" else "Marcar como pago",
                        modifier = Modifier.size(32.dp),
                        tint = if (payment.isPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = payment.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (payment.isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Vencimento:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = payment.dueDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (payment.isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "VALOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "R$ ${String.format("%.2f", payment.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (payment.isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
