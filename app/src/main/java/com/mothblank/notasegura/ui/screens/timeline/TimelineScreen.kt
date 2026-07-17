package com.mothblank.notasegura.ui.screens.timeline

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mothblank.notasegura.domain.model.WarrantyItem
import com.mothblank.notasegura.navigation.AppScreen
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    navController: NavController,
    viewModel: TimelineViewModel = viewModel(
        factory = ViewModelFactory(
            repository = (LocalContext.current.applicationContext as NotaSeguraApplication).repository,
            paymentRepository = (LocalContext.current.applicationContext as NotaSeguraApplication).paymentRepository
        )
    )
) {
    val context = LocalContext.current
    val items by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

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
                    placeholder = { Text("Pesquisar garantias...") },
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

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.onCategorySelected(null) },
                            label = { Text("Todos") }
                        )
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { viewModel.onCategorySelected(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Nenhuma garantia cadastrada",
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
                items(items, key = { it.id }) { item ->
                    var isVisible by remember(item.id) { mutableStateOf(true) }

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

                    LaunchedEffect(isVisible, item.id) {
                        if (!isVisible) {
                            delay(300L)
                            viewModel.deleteItem(context, item)
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
                                WarrantyItemCard(
                                    item = item,
                                    onClick = {
                                        // Navega para a rota de edição, passando o ID do item
                                        navController.navigate(AppScreen.AddEditItem.editRoute(item.id))
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
                        contentDescription = "Deletar Item",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        @Composable
        fun WarrantyItemCard(
            item: WarrantyItem,
            onClick: () -> Unit = {}
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
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp), // Increased padding
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.imagePath != null) {
                        AsyncImage(
                            model = item.imagePath,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp) // Larger image
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.category.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp
                        )

                        Text(
                            text = "Vence em:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.expirationDate.format(dateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
