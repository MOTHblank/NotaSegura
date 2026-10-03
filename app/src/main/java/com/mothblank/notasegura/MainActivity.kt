package com.mothblank.notasegura

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.mothblank.notasegura.data.backup.BackupArchiveManager
import com.mothblank.notasegura.navigation.AppScreen
import com.mothblank.notasegura.ui.components.CreateEncryptedBackupDialog
import com.mothblank.notasegura.ui.components.RestoreEncryptedBackupDialog
import com.mothblank.notasegura.ui.components.UnsavedExitDialog
import com.mothblank.notasegura.ui.screens.add_edit_item.AddEditItemScreen
import com.mothblank.notasegura.ui.screens.add_edit_payment.AddEditPaymentScreen
import com.mothblank.notasegura.ui.screens.payment_details.PaymentDetailsScreen
import com.mothblank.notasegura.ui.screens.payments.PaymentsScreen
import com.mothblank.notasegura.ui.screens.purchase_details.PurchaseDetailsScreen
import com.mothblank.notasegura.ui.screens.reminders.ReminderSettingsScreen
import com.mothblank.notasegura.ui.screens.security.SecurityPrivacyScreen
import com.mothblank.notasegura.ui.screens.timeline.TimelineScreen
import com.mothblank.notasegura.ui.theme.NotaSeguraTheme
import com.mothblank.notasegura.util.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val notificationIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationIntent.value = intent
        enableEdgeToEdge()
        setContent {
            NotaSeguraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NotaSeguraApp(notificationIntent.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationIntent.value = intent
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaSeguraApp(notificationIntent: Intent? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as NotaSeguraApplication
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(notificationIntent) {
        if (notificationIntent?.data?.scheme == "notasegura") {
            navController.handleDeepLink(notificationIntent)
        }
    }

    val isPurchaseEditor = currentRoute == AppScreen.AddEditItem.route
    val isPaymentEditor = currentRoute == AppScreen.AddEditPayment.route
    val isPurchaseDetails = currentRoute == AppScreen.PurchaseDetails.route
    val isPaymentDetails = currentRoute == AppScreen.PaymentDetails.route
    val isSecurityPrivacy = currentRoute == AppScreen.SecurityPrivacy.route
    val isReminderSettings = currentRoute == AppScreen.ReminderSettings.route
    val showPrimaryNavigation =
        currentRoute == AppScreen.Timeline.route || currentRoute == AppScreen.Payments.route

    val editorTitle = when {
        isPurchaseEditor -> {
            if (navBackStackEntry?.arguments?.getString("itemId").isNullOrBlank()) {
                stringResource(R.string.screen_add_purchase)
            } else {
                stringResource(R.string.screen_edit_purchase)
            }
        }
        isPaymentEditor -> {
            if (navBackStackEntry?.arguments?.getString("paymentId").isNullOrBlank()) {
                stringResource(R.string.screen_add_payment)
            } else {
                stringResource(R.string.screen_edit_payment)
            }
        }
        isPurchaseDetails -> stringResource(R.string.screen_purchase_details)
        isPaymentDetails -> stringResource(R.string.screen_payment_details)
        isSecurityPrivacy -> stringResource(R.string.security_screen_title)
        isReminderSettings -> stringResource(R.string.reminder_settings_title)
        else -> null
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEditorExit by remember { mutableStateOf(false) }
    var editorHasUnsavedChanges by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        editorHasUnsavedChanges = false
        pendingEditorExit = false
    }

    val requestEditorExit: () -> Unit = {
        if (editorHasUnsavedChanges) {
            pendingEditorExit = true
        } else {
            navController.popBackStack()
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null

        if (uri != null && password != null) {
            coroutineScope.launch {
                app.backupArchiveManager.createBackup(uri, password)
                    .onSuccess { summary ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.backup_saved_summary,
                                summary.purchases,
                                summary.attachments,
                                summary.payments
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_save_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingRestoreUri = uri
    }

    Scaffold(
        topBar = {
            if (editorTitle != null) {
                TopAppBar(
                    title = {
                        Text(
                            editorTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (isPurchaseEditor || isPaymentEditor) {
                                    requestEditorExit()
                                } else {
                                    navController.popBackStack()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                )
            } else if (showPrimaryNavigation) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_display_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = { menuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null
                            )
                            Text(
                                stringResource(R.string.menu_options),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_report)) },
                                leadingIcon = {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    coroutineScope.launch {
                                        val purchases = app.purchaseDocumentStore
                                            .getAllPurchases()
                                            .first()
                                        val payments = app.paymentRepository
                                            .getAllPayments()
                                            .first()
                                        val file = withContext(Dispatchers.IO) {
                                            ExportManager.createPdf(
                                                context,
                                                purchases,
                                                payments
                                            )
                                        }
                                        if (file != null) {
                                            ExportManager.sharePdf(context, file)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.report_generation_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_save_backup)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    showBackupPasswordDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restore_backup)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    openBackupLauncher.launch(arrayOf("*/*"))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_configure_notifications)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Notifications, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(AppScreen.ReminderSettings.route)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_security_privacy)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(AppScreen.SecurityPrivacy.route)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_privacy_policy)) },
                                onClick = {
                                    menuExpanded = false
                                    val privacyPolicyUri = Uri.parse(
                                        context.getString(R.string.privacy_policy_url)
                                    )
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, privacyPolicyUri)
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.privacy_policy_open_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (showPrimaryNavigation) {
                NavigationBar {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_purchases)) },
                        selected = currentRoute == AppScreen.Timeline.route,
                        onClick = {
                            navController.navigate(AppScreen.Timeline.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_payments)) },
                        selected = currentRoute == AppScreen.Payments.route,
                        onClick = {
                            navController.navigate(AppScreen.Payments.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (showPrimaryNavigation) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (currentRoute == AppScreen.Timeline.route) {
                            navController.navigate(AppScreen.AddEditItem.createRoute())
                        } else {
                            navController.navigate(AppScreen.AddEditPayment.createRoute())
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    text = {
                        Text(
                            if (currentRoute == AppScreen.Timeline.route) {
                                stringResource(R.string.screen_add_purchase)
                            } else {
                                stringResource(R.string.screen_add_payment)
                            }
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
            composable(
                route = AppScreen.Timeline.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "notasegura://purchases" }
                )
            ) {
                TimelineScreen(navController)
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
                AddEditItemScreen(
                    navController = navController,
                    onDirtyStateChanged = { editorHasUnsavedChanges = it },
                    onRequestExit = requestEditorExit
                )
            }
            composable(
                route = AppScreen.PurchaseDetails.route,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "notasegura://purchase/{itemId}" }
                )
            ) {
                PurchaseDetailsScreen(navController)
            }
            composable(
                route = AppScreen.Payments.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "notasegura://payments" }
                )
            ) {
                PaymentsScreen(navController)
            }
            composable(AppScreen.ReminderSettings.route) {
                ReminderSettingsScreen(
                    onOpenSystemSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                )
            }
            composable(AppScreen.SecurityPrivacy.route) {
                SecurityPrivacyScreen(
                    onOpenPrivacyPolicy = {
                        val privacyPolicyUri = Uri.parse(
                            context.getString(R.string.privacy_policy_url)
                        )
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, privacyPolicyUri)
                            )
                        }.onFailure {
                            Toast.makeText(
                                context,
                                context.getString(R.string.privacy_policy_open_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            composable(
                route = AppScreen.PaymentDetails.route,
                arguments = listOf(
                    navArgument("paymentId") {
                        type = NavType.StringType
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "notasegura://payment/{paymentId}" }
                )
            ) {
                PaymentDetailsScreen(navController)
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
                AddEditPaymentScreen(
                    navController = navController,
                    onDirtyStateChanged = { editorHasUnsavedChanges = it },
                    onRequestExit = requestEditorExit
                )
            }
        }
    }

    if (pendingEditorExit) {
        UnsavedExitDialog(
            onKeepEditing = { pendingEditorExit = false },
            onDiscardAndExit = {
                pendingEditorExit = false
                navController.popBackStack()
            }
        )
    }

    if (showBackupPasswordDialog) {
        CreateEncryptedBackupDialog(
            minPasswordLength = BackupArchiveManager.MIN_PASSWORD_LENGTH,
            onDismiss = { showBackupPasswordDialog = false },
            onConfirm = { password ->
                showBackupPasswordDialog = false
                pendingBackupPassword = password
                createBackupLauncher.launch(
                    "NotaSegura_Backup_${LocalDate.now()}.notasegura"
                )
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreEncryptedBackupDialog(
            minPasswordLength = BackupArchiveManager.MIN_PASSWORD_LENGTH,
            onDismiss = { pendingRestoreUri = null },
            onConfirm = { password ->
                pendingRestoreUri = null
                coroutineScope.launch {
                    app.backupArchiveManager.restoreBackup(uri, password)
                        .onSuccess { summary ->
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.backup_restored_summary,
                                    summary.purchases,
                                    summary.attachments,
                                    summary.payments
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .onFailure {
                            pendingRestoreUri = uri
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_restore_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        )
    }
}
