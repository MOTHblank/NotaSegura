package com.mothblank.notasegura.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mothblank.notasegura.R
import com.mothblank.notasegura.domain.model.AttachmentType

@Composable
fun attachmentTypeLabel(type: String): String =
    when (AttachmentType.normalize(type)) {
        AttachmentType.RECEIPT -> stringResource(R.string.attachment_type_receipt)
        AttachmentType.INVOICE -> stringResource(R.string.attachment_type_invoice)
        AttachmentType.WARRANTY -> stringResource(R.string.attachment_type_warranty)
        AttachmentType.MANUAL -> stringResource(R.string.attachment_type_manual)
        else -> stringResource(R.string.attachment_type_other)
    }
