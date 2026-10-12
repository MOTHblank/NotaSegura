package com.mothblank.notasegura.ui.screens.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.R
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.RecurringPaymentPlan
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.ui.AccessibilityTags
import com.mothblank.notasegura.util.CurrencyUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val monthFormat = DateTimeFormatter.ofPattern("MM/yyyy")

@Composable
fun PaymentsScreen(
    navController: NavController,
    viewModel: PaymentsViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(app.purchaseDocumentStore, app.paymentRepository)
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val tab by viewModel.tab.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(AccessibilityTags.PAYMENT_SEARCH),
            label = { Text(stringResource(R.string.payment_search_label)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        val tabs = PaymentListTab.entries
        TabRow(selectedTabIndex = tabs.indexOf(tab)) {
            tabs.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { viewModel.selectTab(item) },
                    text = {
                        Text(
                            when (item) {
                                PaymentListTab.PENDING -> stringResource(R.string.payment_tab_pending)
                                PaymentListTab.RECURRING -> stringResource(R.string.payment_tab_recurring)
                                PaymentListTab.HISTORY -> stringResource(R.string.payment_tab_history)
                            }
                        )
                    }
                )
            }
        }

        when (tab) {
            PaymentListTab.PENDING -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "summary") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.payment_total_pending),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    CurrencyUtils.formatCents(state.pendingTotal),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.overdueCount > 0) {
                                    Text(
                                        stringResource(R.string.payment_overdue_count, state.overdueCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(
                                onClick = { navController.navigate(AppScreen.ReminderSettings.route) }
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.menu_configure_notifications)
                                )
                            }
                        }
                    }
                    if (state.pending.isEmpty()) {
                        item { PaymentEmptyState(query.isNotBlank(), false) {
                            navController.navigate(AppScreen.AddEditPayment.createRoute())
                        } }
                    }
                    items(state.pending, key = { it.id }) { payment ->
                        CompactPaymentCard(
                            payment = payment,
                            onOpen = {
                                navController.navigate(AppScreen.PaymentDetails.createRoute(payment.id))
                            },
                            onPay = { viewModel.togglePaidStatus(payment) }
                        )
                    }
                }
            }
            PaymentListTab.RECURRING -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.plans.isEmpty()) {
                        item { PaymentEmptyState(query.isNotBlank(), true) {
                            navController.navigate(AppScreen.AddEditPayment.createRoute())
                        } }
                    }
                    items(state.plans, key = { it.id }) { plan ->
                        RecurrencePlanCard(
                            plan = plan,
                            upcoming = state.payments
                                .filter { it.seriesId == plan.id && !it.isPaid }
                                .minByOrNull { it.dueDate },
                            onOpen = {
                                state.payments.filter { it.seriesId == plan.id }
                                    .minByOrNull { it.dueDate }
                                    ?.let { payment ->
                                        navController.navigate(
                                            AppScreen.PaymentDetails.createRoute(payment.id)
                                        )
                                    }
                            }
                        )
                    }
                }
            }
            PaymentListTab.HISTORY -> {
                val grouped = state.paid.groupBy {
                    YearMonth.from(it.paidAt ?: it.dueDate)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (grouped.isEmpty()) {
                        item { PaymentEmptyState(query.isNotBlank(), false) {
                            navController.navigate(AppScreen.AddEditPayment.createRoute())
                        } }
                    }
                    grouped.forEach { (month, paid) ->
                        item(key = "month-$month") {
                            Text(
                                month.format(monthFormat),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(paid, key = { it.id }) { payment ->
                            CompactPaymentCard(
                                payment = payment,
                                onOpen = {
                                    navController.navigate(AppScreen.PaymentDetails.createRoute(payment.id))
                                },
                                onPay = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPaymentCard(payment: Payment, onOpen: () -> Unit, onPay: (() -> Unit)?) {
    val today = LocalDate.now()
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        payment.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (payment.seriesId != null) Icons.Default.Repeat else Icons.Default.Event,
                            contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (payment.seriesId != null) {
                                stringResource(R.string.payment_monthly_short)
                            } else {
                                stringResource(R.string.payment_once)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    CurrencyUtils.formatCents(payment.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val statusText = when {
                    payment.isPaid -> stringResource(
                        R.string.payment_paid_on,
                        (payment.paidAt ?: payment.dueDate).format(dateFormat)
                    )
                    payment.dueDate.isBefore(today) -> stringResource(
                        R.string.payment_was_due_on, payment.dueDate.format(dateFormat)
                    )
                    else -> stringResource(
                        R.string.payment_due_on, payment.dueDate.format(dateFormat)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        when {
                            payment.isPaid -> Icons.Default.CheckCircle
                            payment.dueDate.isBefore(today) -> Icons.Default.WarningAmber
                            else -> Icons.Default.Event
                        },
                        contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (!payment.isPaid && payment.dueDate.isBefore(today)) {
                            MaterialTheme.colorScheme.error
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(statusText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onPay != null) {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.padding(start = 8.dp).heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(stringResource(R.string.payment_mark_paid))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurrencePlanCard(
    plan: RecurringPaymentPlan,
    upcoming: Payment?,
    onOpen: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(CurrencyUtils.formatCents(plan.amountCents),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.payment_every_month_day, plan.anchorDay),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                when {
                    !plan.isActive -> stringResource(R.string.payment_series_stopped)
                    upcoming != null -> stringResource(
                        R.string.payment_series_next, upcoming.dueDate.format(dateFormat)
                    )
                    else -> stringResource(R.string.payment_series_no_due)
                },
                color = if (plan.isActive) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PaymentEmptyState(filtered: Boolean, recurring: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                filtered -> stringResource(R.string.payment_empty_filtered_title)
                recurring -> stringResource(R.string.payment_no_recurring)
                else -> stringResource(R.string.payment_no_items_for_tab)
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (!filtered) {
            TextButton(onClick = onCreate) {
                Text(stringResource(R.string.screen_add_payment))
            }
        }
    }
}
