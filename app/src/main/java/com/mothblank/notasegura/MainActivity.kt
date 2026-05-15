package com.mothblank.notasegura

import com.mothblank.notasegura.util.ExportManager
import androidx.compose.ui.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.ui.screens.add_edit_item.AddEditItemScreen
import com.mothblank.notasegura.ui.screens.timeline.TimelineScreen
import com.mothblank.notasegura.ui.theme.NotaSeguraTheme
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mothblank.notasegura.ui.screens.payments.PaymentsScreen
import com.mothblank.notasegura.ui.screens.add_edit_payment.AddEditPaymentScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotaSeguraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NotaSeguraApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaSeguraApp() {
    val context = LocalContext.current
    val app = context.applicationContext as NotaSeguraApplication

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                } else {
                }
            }
        )
        LaunchedEffect(key1 = true) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Nota Segura", 
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) 
                },
                actions = {
                    IconButton(onClick = {
                        app.scope.launch {
                            val warranties = app.repository.getAllItems().first()
                            val payments = app.paymentRepository.getAllPayments().first()
                            ExportManager.exportToPdf(context, warranties, payments)
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(32.dp)) },
                    label = { Text("Garantias", style = MaterialTheme.typography.labelLarge) },
                    selected = currentRoute == AppScreen.Timeline.route,
                    onClick = {
                        navController.navigate(AppScreen.Timeline.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(32.dp)) },
                    label = { Text("Pagamentos", style = MaterialTheme.typography.labelLarge) },
                    selected = currentRoute == AppScreen.Payments.route,
                    onClick = {
                        navController.navigate(AppScreen.Payments.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == AppScreen.Timeline.route || currentRoute == AppScreen.Payments.route) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (currentRoute == AppScreen.Timeline.route) {
                            navController.navigate(AppScreen.AddEditItem.createRoute())
                        } else {
                            navController.navigate(AppScreen.AddEditPayment.createRoute())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    text = { 
                        Text(
                            if (currentRoute == AppScreen.Timeline.route) "Nova Garantia" else "Novo Pagamento",
                            style = MaterialTheme.typography.titleMedium
                        ) 
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppScreen.Timeline.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = AppScreen.Timeline.route) {
                TimelineScreen(navController = navController)
            }

            composable(
                route = AppScreen.AddEditItem.route,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) {
                AddEditItemScreen(navController = navController)
            }

            composable(route = AppScreen.Payments.route) {
                PaymentsScreen(navController = navController)
            }

            composable(
                route = AppScreen.AddEditPayment.route,
                arguments = listOf(
                    navArgument("paymentId") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) {
                AddEditPaymentScreen(navController = navController)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    NotaSeguraTheme {
        NotaSeguraApp()
    }
}