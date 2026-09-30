package com.mothblank.notasegura.ui.screens.add_edit_payment

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.ui.components.ReminderPermissionDialog
import com.mothblank.notasegura.util.DateUtils
import com.mothblank.notasegura.util.NotificationPermissionPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPaymentScreen(
    navController: NavController,
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onRequestExit: () -> Unit = { navController.popBackStack() },
    viewModel: AddEditPaymentViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isDirty) {
        onDirtyStateChanged(isDirty)
    }

    BackHandler(onBack = onRequestExit)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        navController.popBackStack()
    }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            if (NotificationPermissionPolicy.shouldOfferAfterSuccessfulSave(context)) {
                showReminderPermissionDialog = true
            } else {
                navController.popBackStack()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "* Campos obrigatórios",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PaymentFormSection(
            title = "Pagamento",
            subtitle = "Dados principais da cobrança."
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { RequiredPaymentLabel("Nome da conta") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { RequiredPaymentLabel("Valor (R$)") },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                isError = uiState.amountError != null,
                supportingText = {
                    uiState.amountError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Box {
                OutlinedTextField(
                    value = viewModel.formatDate(uiState.dueDate),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { RequiredPaymentLabel("Data de vencimento") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                )
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            onClickLabel = "Selecionar data de vencimento",
                            role = Role.Button
                        ) { showDatePicker = true }
                )
            }
        }

        PaymentFormSection(
            title = "Opções",
            subtitle = "Defina o estado atual e se a cobrança se repete."
        ) {
            PaymentCheckboxRow(
                checked = uiState.isPaid,
                title = "Já está pago",
                supportingText = "Registra este pagamento como concluído.",
                onClick = { viewModel.onPaidChange(!uiState.isPaid) }
            )

            PaymentCheckboxRow(
                checked = uiState.isRecurring,
                title = "Pagamento mensal",
                supportingText = "Cria a próxima ocorrência quando esta for marcada como paga.",
                onClick = { viewModel.onRecurringChange(!uiState.isRecurring) }
            )
        }

        uiState.errorMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = viewModel::savePayment,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            enabled = !uiState.isSaving &&
                uiState.title.isNotBlank() &&
                uiState.dueDate != null,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    "Salvar pagamento",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showReminderPermissionDialog) {
        ReminderPermissionDialog(
            onEnable = {
                NotificationPermissionPolicy.markOfferHandled(context)
                showReminderPermissionDialog = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                NotificationPermissionPolicy.markOfferHandled(context)
                showReminderPermissionDialog = false
                navController.popBackStack()
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        val confirmEnabled = remember {
            derivedStateOf { datePickerState.selectedDateMillis != null }
        }

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.onDueDateChange(DateUtils.datePickerMillisToLocalDate(it))
                        }
                        showDatePicker = false
                    },
                    enabled = confirmEnabled.value
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PaymentFormSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun RequiredPaymentLabel(text: String) {
    Text(
        buildAnnotatedString {
            append("$text ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append("*")
            }
        }
    )
}

@Composable
private fun PaymentCheckboxRow(
    checked: Boolean,
    title: String,
    supportingText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(48.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
