package com.mothblank.notasegura.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Visual marker, paired with an explicit accessible field description. */
@Composable
fun RequiredFieldLabel(label: String) {
    val errorColor = MaterialTheme.colorScheme.error
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(color = errorColor)) { append("*") }
        }
    )
}
