package com.mothblank.notasegura.ui.screens.add_edit_item

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.util.CurrencyUtils
import com.mothblank.notasegura.util.DateUtils
import com.mothblank.notasegura.util.NotificationPermissionPolicy
import java.io.File
import java.time.LocalDate

private fun createCameraUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    navController: NavController,
    viewModel: AddEditItemViewModel = viewModel(
        factory = (LocalContext.current.applicationContext as NotaSeguraApplication).let { app ->
            ViewModelFactory(
                purchaseDocumentStore = app.purchaseDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var showAttachmentOptions by remember { mutableStateOf(false) }
    var tempImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showPurchaseDatePicker by remember { mutableStateOf(false) }
    var showWarrantyDatePicker by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            if (NotificationPermissionPolicy.shouldRequestAfterSuccessfulSave(context)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            navController.popBackStack()
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onAttachmentSelected(context, it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let(Uri::parse)?.let {
                viewModel.onAttachmentSelected(context, it)
            }
        }
        tempImageUri = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "Compra e documentos",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        AttachmentSection(
            attachments = uiState.attachments,
            isAnalyzing = uiState.isAnalyzingDocument,
            onAdd = { showAttachmentOptions = true },
            onRemove = viewModel::removeAttachment
        )

        OcrSuggestionsCard(
            state = uiState,
            onMerchant = viewModel::applyMerchantSuggestion,
            onDate = viewModel::applyPurchaseDateSuggestion,
            onValue = viewModel::applyPurchaseValueSuggestion,
            onModel = viewModel::applyModelSuggestion,
            onSerial = viewModel::applySerialSuggestion
        )

        OutlinedTextField(
            value = uiState.productName,
            onValueChange = viewModel::onProductNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { RequiredLabel("Produto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = uiState.merchant,
            onValueChange = viewModel::onMerchantChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Loja / vendedor") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = uiState.purchaseValue,
            onValueChange = viewModel::onPurchaseValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Valor da compra (R$)") },
            singleLine = true,
            isError = uiState.purchaseValueError != null,
            supportingText = {
                uiState.purchaseValueError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = uiState.category,
            onValueChange = viewModel::onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Categoria") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.modelNumber,
                onValueChange = viewModel::onModelNumberChange,
                modifier = Modifier.weight(1f),
                label = { Text("Modelo") },
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.serialNumber,
                onValueChange = viewModel::onSerialNumberChange,
                modifier = Modifier.weight(1f),
                label = { Text("Nº de série") },
                singleLine = true
            )
        }

        DateField(
            value = viewModel.formatDate(uiState.purchaseDate),
            label = "Data da compra",
            required = true,
            clickLabel = "Selecionar data da compra",
            onClick = { showPurchaseDatePicker = true }
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DateField(
                value = viewModel.formatDate(uiState.warrantyEndDate),
                label = "Fim da garantia",
                required = false,
                clickLabel = "Selecionar fim da garantia",
                onClick = { showWarrantyDatePicker = true }
            )
            if (uiState.warrantyEndDate != null) {
                TextButton(
                    onClick = { viewModel.onWarrantyEndDateChange(null) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Remover garantia")
                }
            }
        }

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Observações") },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            )
        )

        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = viewModel::saveItem,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = !uiState.isSaving &&
                !uiState.isAnalyzingDocument &&
                uiState.productName.isNotBlank() &&
                uiState.purchaseDate != null,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("SALVAR", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showPurchaseDatePicker) {
        PurchaseDatePickerDialog(
            onDismiss = { showPurchaseDatePicker = false },
            onSelected = {
                viewModel.onPurchaseDateChange(it)
                showPurchaseDatePicker = false
            }
        )
    }

    if (showWarrantyDatePicker) {
        PurchaseDatePickerDialog(
            onDismiss = { showWarrantyDatePicker = false },
            onSelected = {
                viewModel.onWarrantyEndDateChange(it)
                showWarrantyDatePicker = false
            }
        )
    }

    if (showAttachmentOptions) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentOptions = false }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Tirar foto") },
                    leadingContent = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        showAttachmentOptions = false
                        createCameraUri(context).also { uri ->
                            tempImageUri = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Escolher imagem ou PDF") },
                    leadingContent = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    modifier = Modifier.clickable(role = Role.Button) {
                        showAttachmentOptions = false
                        documentLauncher.launch(arrayOf("image/*", "application/pdf"))
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentSection(
    attachments: List<PurchaseAttachmentUi>,
    isAnalyzing: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Documentos",
                style = MaterialTheme.typography.titleLarge
            )
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    "Lendo documento...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    Card(
                        modifier = Modifier
                            .width(150.dp)
                            .height(150.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (attachment.isImage) {
                                AsyncImage(
                                    model = attachment.path,
                                    contentDescription = attachment.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        attachment.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onRemove(attachment.id) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover documento"
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ADICIONAR DOCUMENTO")
        }
    }
}

@Composable
private fun OcrSuggestionsCard(
    state: AddEditUiState,
    onMerchant: () -> Unit,
    onDate: () -> Unit,
    onValue: () -> Unit,
    onModel: () -> Unit,
    onSerial: () -> Unit
) {
    val suggestions = state.ocrSuggestions ?: return
    val hasSuggestions = suggestions.merchant != null ||
        suggestions.purchaseDate != null ||
        suggestions.purchaseValueCents != null ||
        suggestions.modelNumber != null ||
        suggestions.serialNumber != null

    if (!hasSuggestions) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Dados encontrados no documento",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Revise e aplique apenas o que estiver correto.",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()

            suggestions.merchant?.let {
                SuggestionRow("Loja", it, onMerchant)
            }
            suggestions.purchaseDate?.let {
                SuggestionRow(
                    "Data",
                    it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    onDate
                )
            }
            suggestions.purchaseValueCents?.let {
                SuggestionRow("Valor", CurrencyUtils.formatCents(it), onValue)
            }
            suggestions.modelNumber?.let {
                SuggestionRow("Modelo", it, onModel)
            }
            suggestions.serialNumber?.let {
                SuggestionRow("Nº de série", it, onSerial)
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    label: String,
    value: String,
    onUse: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onUse) {
            Text("Usar")
        }
    }
}

@Composable
private fun RequiredLabel(text: String) {
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
private fun DateField(
    value: String,
    label: String,
    required: Boolean,
    clickLabel: String,
    onClick: () -> Unit
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = {
                if (required) RequiredLabel(label) else Text(label)
            },
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = null)
            }
        )
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    onClickLabel = clickLabel,
                    role = Role.Button,
                    onClick = onClick
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDatePickerDialog(
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState()
    val confirmEnabled = remember {
        derivedStateOf { state.selectedDateMillis != null }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onSelected(DateUtils.datePickerMillisToLocalDate(it))
                    }
                },
                enabled = confirmEnabled.value
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        DatePicker(state = state)
    }
}
