package com.mothblank.notasegura.ui.screens.add_edit_item

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import com.mothblank.notasegura.util.DateUtils
import com.mothblank.notasegura.util.NotificationPermissionPolicy
import java.io.File

private fun createImageUri(context: Context): Uri {
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
                warrantyDocumentStore = app.warrantyDocumentStore,
                paymentRepository = app.paymentRepository
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var showInputOptions by remember { mutableStateOf(false) }
    var tempImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showPurchaseDatePicker by remember { mutableStateOf(false) }
    var showExpirationDatePicker by remember { mutableStateOf(false) }

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

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onImageSelected(context, it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let(Uri::parse)?.let { viewModel.onImageSelected(context, it) }
        }
        tempImageUri = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Adicionar/Editar Item",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (uiState.imagePath != null) {
            AsyncImage(
                model = uiState.imagePath,
                contentDescription = "Imagem do documento anexado",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Alterar imagem do documento"
                    ) { showInputOptions = true },
                contentScale = ContentScale.Crop
            )
            Text(
                "Toque na imagem para alterar",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Button(
                onClick = { showInputOptions = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Text("ADICIONAR DOCUMENTO", style = MaterialTheme.typography.titleMedium)
            }
        }

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                RequiredLabel("Nome do Produto")
            },
            textStyle = MaterialTheme.typography.bodyLarge,
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
            label = { Text("Categoria", style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Datas importantes:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            DateField(
                value = viewModel.formatDate(uiState.purchaseDate),
                label = "Data da Compra",
                clickLabel = "Selecionar data da compra",
                onClick = { showPurchaseDatePicker = true }
            )

            DateField(
                value = viewModel.formatDate(uiState.expirationDate),
                label = "Fim da Garantia",
                clickLabel = "Selecionar data de fim da garantia",
                onClick = { showExpirationDatePicker = true }
            )
        }

        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = viewModel::saveItem,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            enabled = !uiState.isSaving &&
                uiState.name.isNotBlank() &&
                uiState.purchaseDate != null &&
                uiState.expirationDate != null,
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
    }

    if (showPurchaseDatePicker) {
        AppDatePickerDialog(
            onDismiss = { showPurchaseDatePicker = false },
            onSelected = {
                viewModel.onPurchaseDateChange(it)
                showPurchaseDatePicker = false
            }
        )
    }

    if (showExpirationDatePicker) {
        AppDatePickerDialog(
            onDismiss = { showExpirationDatePicker = false },
            onSelected = {
                viewModel.onExpirationDateChange(it)
                showExpirationDatePicker = false
            }
        )
    }

    if (showInputOptions) {
        ModalBottomSheet(onDismissRequest = { showInputOptions = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Tirar Foto") },
                    leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Tirar foto"
                    ) {
                        showInputOptions = false
                        createImageUri(context).also {
                            tempImageUri = it.toString()
                            cameraLauncher.launch(it)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Escolher da Galeria") },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Escolher da galeria"
                    ) {
                        showInputOptions = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
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
        },
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun DateField(
    value: String,
    label: String,
    clickLabel: String,
    onClick: () -> Unit
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { RequiredLabel(label) },
            textStyle = MaterialTheme.typography.bodyLarge,
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(32.dp))
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
private fun AppDatePickerDialog(
    onDismiss: () -> Unit,
    onSelected: (java.time.LocalDate) -> Unit
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
