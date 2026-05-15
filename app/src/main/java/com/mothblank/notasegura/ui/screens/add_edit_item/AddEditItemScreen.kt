package com.mothblank.notasegura.ui.screens.add_edit_item

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mothblank.notasegura.NotaSeguraApplication
import com.mothblank.notasegura.ViewModelFactory
import android.content.Context
import android.widget.Toast

import androidx.compose.foundation.layout.*

import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File

import androidx.compose.material3.ListItem

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage

import java.time.Instant

import java.time.ZoneId

fun createImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "new_image_${System.currentTimeMillis()}.jpg")
    val authority = "${context.packageName}.provider"
    return FileProvider.getUriForFile(context, authority, imageFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    navController: NavController,
    viewModel: AddEditItemViewModel = viewModel(
        factory = ViewModelFactory(
            repository = (LocalContext.current.applicationContext as NotaSeguraApplication).repository,
            paymentRepository = (LocalContext.current.applicationContext as NotaSeguraApplication).paymentRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showInputOptions by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> uri?.let { viewModel.onImageSelected(context, it) } }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempImageUri?.let { viewModel.onImageSelected(context, it) }
            }
        }
    )

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                Toast.makeText(context, "PDF selecionado. Processamento futuro.", Toast.LENGTH_LONG).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp) // Increased padding
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp) // Increased spacing
    ) {
        Text(
            "Adicionar/Editar Item", 
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (uiState.imagePath != null) {
            AsyncImage(
                model = uiState.imagePath,
                contentDescription = "Nota Fiscal",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp) // Larger image preview
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showInputOptions = true },
                contentScale = ContentScale.Crop
            )
            Text(
                "Toque na imagem para alterar", 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Button(
                onClick = { showInputOptions = true },
                modifier = Modifier.fillMaxWidth().height(64.dp), // Larger button
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
            label = { Text("Nome do Produto", style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.category, 
            onValueChange = viewModel::onCategoryChange, 
            modifier = Modifier.fillMaxWidth(), 
            label = { Text("Categoria", style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true
        )

        var showPurchaseDatePicker by remember { mutableStateOf(false) }
        var showExpirationDatePicker by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Datas importantes:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Box {
                OutlinedTextField(
                    value = viewModel.formatDate(uiState.purchaseDate), 
                    onValueChange = {}, 
                    modifier = Modifier.fillMaxWidth(), 
                    label = { Text("Data da Compra") }, 
                    textStyle = MaterialTheme.typography.bodyLarge,
                    readOnly = true, 
                    trailingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(32.dp)) }
                )
                Spacer(modifier = Modifier.matchParentSize().clickable { showPurchaseDatePicker = true })
            }

            Box {
                OutlinedTextField(
                    value = viewModel.formatDate(uiState.expirationDate), 
                    onValueChange = {}, 
                    modifier = Modifier.fillMaxWidth(), 
                    label = { Text("Fim da Garantia") }, 
                    textStyle = MaterialTheme.typography.bodyLarge,
                    readOnly = true, 
                    trailingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(32.dp)) }
                )
                Spacer(modifier = Modifier.matchParentSize().clickable { showExpirationDatePicker = true })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { viewModel.saveItem(); navController.popBackStack() }, 
            modifier = Modifier.fillMaxWidth().height(72.dp), // Extra large save button
            enabled = uiState.name.isNotBlank() && uiState.purchaseDate != null && uiState.expirationDate != null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("SALVAR", style = MaterialTheme.typography.titleLarge)
        }

        if (showPurchaseDatePicker) {
            val datePickerState = rememberDatePickerState()
            val confirmEnabled = remember { derivedStateOf { datePickerState.selectedDateMillis != null } }
            DatePickerDialog(
                onDismissRequest = { showPurchaseDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPurchaseDatePicker = false
                            datePickerState.selectedDateMillis?.let {
                                val selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                viewModel.onPurchaseDateChange(selectedDate)
                            }
                        },
                        enabled = confirmEnabled.value
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showPurchaseDatePicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = datePickerState) }
        }

        if (showExpirationDatePicker) {
            val datePickerState = rememberDatePickerState()
            val confirmEnabled = remember { derivedStateOf { datePickerState.selectedDateMillis != null } }
            DatePickerDialog(
                onDismissRequest = { showExpirationDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExpirationDatePicker = false
                            datePickerState.selectedDateMillis?.let {
                                val selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                viewModel.onExpirationDateChange(selectedDate)
                            }
                        },
                        enabled = confirmEnabled.value
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showExpirationDatePicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = datePickerState) }
        }
    }

    if (showInputOptions) {
        ModalBottomSheet(onDismissRequest = { showInputOptions = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Tirar Foto") },
                    leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showInputOptions = false
                        val newUri = createImageUri(context)
                        tempImageUri = newUri
                        cameraLauncher.launch(newUri)
                    }
                )
                ListItem(
                    headlineContent = { Text("Escolher da Galeria") },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showInputOptions = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                ListItem(
                    headlineContent = { Text("Selecionar PDF") },
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showInputOptions = false
                        pdfLauncher.launch("application/pdf")
                    }
                )
            }
        }
    }
}