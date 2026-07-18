package com.mothblank.notasegura.ui.screens.add_edit_payment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPaymentScreen(
    navController: NavController,
    viewModel: AddEditPaymentViewModel = viewModel(
        factory = ViewModelFactory(
            repository = (LocalContext.current.applicationContext as NotaSeguraApplication).repository,
            paymentRepository = (LocalContext.current.applicationContext as NotaSeguraApplication).paymentRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Adicionar/Editar Pagamento", 
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = uiState.title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome da Conta", style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.amount,
            onValueChange = viewModel::onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Valor (R$)", style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = uiState.amountError != null,
            supportingText = {
                if (uiState.amountError != null) {
                    Text(
                        text = uiState.amountError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        var showDatePicker by remember { mutableStateOf(false) }

        Box {
            OutlinedTextField(
                value = viewModel.formatDate(uiState.dueDate),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Data de Vencimento", style = MaterialTheme.typography.titleMedium) },
                textStyle = MaterialTheme.typography.bodyLarge,
                readOnly = true,
                trailingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(32.dp)) }
            )
            Spacer(modifier = Modifier.matchParentSize().clickable(
                onClickLabel = "Selecionar data de vencimento",
                role = Role.Button
            ) { showDatePicker = true })
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onPaidChange(!uiState.isPaid) }
                ) {
                    Checkbox(
                        checked = uiState.isPaid,
                        onCheckedChange = viewModel::onPaidChange,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("JÁ ESTÁ PAGO", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onRecurringChange(!uiState.isRecurring) }
                ) {
                    Checkbox(
                        checked = uiState.isRecurring,
                        onCheckedChange = viewModel::onRecurringChange,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("PAGAMENTO MENSAL", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (viewModel.savePayment()) {
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            enabled = uiState.title.isNotBlank() && uiState.dueDate != null,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("SALVAR", style = MaterialTheme.typography.titleLarge)
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            val confirmEnabled = remember { derivedStateOf { datePickerState.selectedDateMillis != null } }
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDatePicker = false
                            datePickerState.selectedDateMillis?.let {
                                val selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                viewModel.onDueDateChange(selectedDate)
                            }
                        },
                        enabled = confirmEnabled.value
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = datePickerState) }
        }
    }
}
