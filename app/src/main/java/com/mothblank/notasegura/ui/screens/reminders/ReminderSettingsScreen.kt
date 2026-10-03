package com.mothblank.notasegura.ui.screens.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mothblank.notasegura.R
import com.mothblank.notasegura.data.worker.ExpirationCheckWorker
import com.mothblank.notasegura.util.ReminderPreferences

@Composable
fun ReminderSettingsScreen(
    onOpenSystemSettings: () -> Unit
) {
    val context = LocalContext.current
    var paymentDays by remember {
        mutableIntStateOf(ReminderPreferences.paymentLeadDays(context))
    }
    var warrantyDays by remember {
        mutableIntStateOf(ReminderPreferences.warrantyLeadDays(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.reminder_settings_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ReminderThresholdCard(
            title = stringResource(R.string.reminder_payment_title),
            body = stringResource(R.string.reminder_payment_body),
            selectedDays = paymentDays,
            options = ReminderPreferences.paymentLeadDayOptions,
            onSelected = { days ->
                paymentDays = days
                ReminderPreferences.setPaymentLeadDays(context, days)
                refreshReminders(context)
            }
        )

        ReminderThresholdCard(
            title = stringResource(R.string.reminder_warranty_title),
            body = stringResource(R.string.reminder_warranty_body),
            selectedDays = warrantyDays,
            options = ReminderPreferences.warrantyLeadDayOptions,
            onSelected = { days ->
                warrantyDays = days
                ReminderPreferences.setWarrantyLeadDays(context, days)
                refreshReminders(context)
            }
        )

        OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Text(
                stringResource(R.string.reminder_open_android_settings),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            stringResource(R.string.reminder_settings_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun refreshReminders(context: android.content.Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "reminder_settings_refresh",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<ExpirationCheckWorker>().build()
    )
}

@Composable
private fun ReminderThresholdCard(
    title: String,
    body: String,
    selectedDays: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { onSelected(days) },
                        label = {
                            Text(
                                if (days == 0) {
                                    stringResource(R.string.reminder_disabled)
                                } else {
                                    pluralStringResource(
                                        R.plurals.reminder_days_before,
                                        days,
                                        days
                                    )
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
