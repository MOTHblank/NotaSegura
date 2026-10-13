package com.mothblank.notasegura.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import com.mothblank.notasegura.R
import java.time.LocalDate

/** Independent keyboard entry and calendar button shared across all date fields. */
@Composable
fun EditableDateField(
    value: String,
    label: String,
    required: Boolean,
    clickLabel: String,
    fieldTag: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onOpenCalendar: () -> Unit
) {
    val accessibleLabel = if (required) {
        stringResource(R.string.field_required_semantics, label)
    } else {
        label
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldTag)
            .semantics { contentDescription = accessibleLabel },
        label = {
            if (required) {
                Text(buildAnnotatedString {
                    append("$label ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
                })
            } else {
                Text(label)
            }
        },
        placeholder = { Text(stringResource(R.string.date_input_hint)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        trailingIcon = {
            IconButton(onClick = onOpenCalendar) {
                Icon(Icons.Default.DateRange, contentDescription = clickLabel)
            }
        },
        supportingText = {
            Text(
                stringResource(if (isError) R.string.error_invalid_date else R.string.date_input_hint),
                color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** Android platform picker avoids the previous Compose calendar/field interception. */
@Composable
fun AndroidCalendarDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestSelected by rememberUpdatedState(onSelected)

    DisposableEffect(context) {
        val date = initialDate ?: LocalDate.now()
        val dialog = DatePickerDialog(
            context,
            { _, year, zeroBasedMonth, dayOfMonth ->
                latestSelected(LocalDate.of(year, zeroBasedMonth + 1, dayOfMonth))
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        )
        dialog.setOnDismissListener { latestDismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
