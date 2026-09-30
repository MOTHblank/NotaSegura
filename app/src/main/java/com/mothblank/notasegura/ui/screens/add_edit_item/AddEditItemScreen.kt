package com.mothblank.notasegura.ui.screens.add_edit_item

import android.Manifest
import android.content.Context
import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mothblank.notasegura.ui.components.DocumentViewerDialog
import com.mothblank.notasegura.ui.components.ReminderPermissionDialog
import com.mothblank.notasegura.ui.components.UnsavedExitDialog
import com.mothblank.notasegura.util.CurrencyUtils
import com.mothblank.notasegura.util.DateUtils
import com.mothblank.notasegura.util.NotificationPermissionPolicy
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    var previewAttachment by remember { mutableStateOf<PurchaseAttachmentUi?>(null) }
    var showReminderPermissionDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

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

    BackHandler {
        showExitDialog = true
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "* Campos obrigatórios",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FormSection(
            title = "Comprovante / documento",
            subtitle = "Comece por aqui: adicione uma foto ou imagem para extrair dados da compra. PDFs também podem ser anexados."
        ) {
            AttachmentSection(
                attachments = uiState.attachments,
                isAnalyzing = uiState.isAnalyzingDocument,
                onAdd = { showAttachmentOptions = true },
                onPreview = { previewAttachment = it },
                onRemove = viewModel::removeAttachment
            )
        }

        FormSection(
            title = "Compra",
            subtitle = "Quando e onde a compra foi feita."
        ) {
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
            uiState.ocrSuggestions?.merchant?.let {
                FieldSuggestion(
                    value = it,
                    onUse = viewModel::applyMerchantSuggestion
                )
            }

            OutlinedTextField(
                value = uiState.purchaseValue,
                onValueChange = viewModel::onPurchaseValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor da compra (R$)") },
                singleLine = true,
                isError = uiState.purchaseValueError != null,
                supportingText = {
                    uiState.purchaseValueError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )
            uiState.ocrSuggestions?.purchaseValueCents?.let {
                FieldSuggestion(
                    value = CurrencyUtils.formatCents(it),
                    onUse = viewModel::applyPurchaseValueSuggestion
                )
            }

            DateField(
                value = viewModel.formatDate(uiState.purchaseDate),
                label = "Data da compra",
                required = true,
                clickLabel = "Selecionar data da compra",
                onClick = { showPurchaseDatePicker = true }
            )
            uiState.ocrSuggestions?.purchaseDate?.let {
                FieldSuggestion(
                    value = it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    onUse = viewModel::applyPurchaseDateSuggestion
                )
            }
        }

        FormSection(
            title = "Produto",
            subtitle = "Identifique o item para encontrá-lo depois."
        ) {
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = uiState.modelNumber,
                    onValueChange = viewModel::onModelNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modelo") },
                    singleLine = true
                )
                uiState.ocrSuggestions?.modelNumber?.let {
                    FieldSuggestion(
                        value = it,
                        onUse = viewModel::applyModelSuggestion
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = uiState.serialNumber,
                    onValueChange = viewModel::onSerialNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nº de série") },
                    singleLine = true
                )
                uiState.ocrSuggestions?.serialNumber?.let {
                    FieldSuggestion(
                        value = it,
                        onUse = viewModel::applySerialSuggestion
                    )
                }
            }
        }

        FormSection(
            title = "Garantia",
            subtitle = "Opcional. Deixe em branco se não houver garantia registrada."
        ) {
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

        FormSection(
            title = "Observações",
            subtitle = "Informações extras que não cabem nos campos acima."
        ) {
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
        }

        uiState.errorMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = viewModel::saveItem,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
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
                Text("Salvar compra", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    supportingContent = { Text("Fotografe um comprovante ou certificado.") },
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
                    supportingContent = { Text("Selecione um documento já salvo no aparelho.") },
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

    if (showExitDialog) {
        UnsavedExitDialog(
            onKeepEditing = { showExitDialog = false },
            onDiscardAndExit = {
                showExitDialog = false
                navController.popBackStack()
            }
        )
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

    previewAttachment?.let { attachment ->
        DocumentViewerDialog(
            path = attachment.path,
            mimeType = attachment.mimeType,
            displayName = attachment.displayName,
            onDismiss = { previewAttachment = null }
        )
    }
}

@Composable
private fun FormSection(
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
private fun FieldSuggestion(
    value: String,
    onUse: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Encontrado: $value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUse) {
                Text("Usar")
            }
        }
    }
}

@Composable
private fun AttachmentSection(
    attachments: List<PurchaseAttachmentUi>,
    isAnalyzing: Boolean,
    onAdd: () -> Unit,
    onPreview: (PurchaseAttachmentUi) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isAnalyzing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

        if (attachments.isEmpty()) {
            Text(
                "Nenhum documento anexado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Toque em um documento para abrir e ampliar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    Column(
                        modifier = Modifier.width(156.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Card(
                            onClick = { onPreview(attachment) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(156.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (attachment.isImage) {
                                    AsyncImage(
                                        model = attachment.path,
                                        contentDescription = "Abrir ${attachment.displayName}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(46.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            attachment.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3
                                        )
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { onRemove(attachment.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Remover",
                                modifier = Modifier.padding(start = 6.dp),
                                color = MaterialTheme.colorScheme.error
                            )
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
            Text("Adicionar foto ou documento")
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
