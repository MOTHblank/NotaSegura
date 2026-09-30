package com.mothblank.notasegura.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mothblank.notasegura.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DocumentViewerDialog(
    path: String,
    mimeType: String,
    displayName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Text(
                                stringResource(R.string.common_close),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                    }
                    Text(
                        stringResource(R.string.document_viewer_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                when {
                    mimeType.startsWith("image/") -> {
                        ZoomableImage(
                            path = path,
                            contentDescription = displayName,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    mimeType == "application/pdf" -> {
                        PdfDocumentViewer(
                            path = path,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(stringResource(R.string.document_viewer_unavailable))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    path: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformableState),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ZoomableBitmap(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(transformableState),
        contentScale = ContentScale.Fit
    )
}

private sealed interface PdfRenderState {
    data object Loading : PdfRenderState
    data class Ready(
        val bitmap: Bitmap,
        val pageCount: Int,
        val pageIndex: Int
    ) : PdfRenderState
    data class Error(val message: String) : PdfRenderState
}

@Composable
private fun PdfDocumentViewer(
    path: String,
    modifier: Modifier = Modifier
) {
    var requestedPage by remember(path) { mutableIntStateOf(0) }

    val renderState by produceState<PdfRenderState>(
        initialValue = PdfRenderState.Loading,
        path,
        requestedPage
    ) {
        value = PdfRenderState.Loading
        value = withContext(Dispatchers.IO) {
            renderPdfPage(path, requestedPage)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            when (val state = renderState) {
                PdfRenderState.Loading -> CircularProgressIndicator()
                is PdfRenderState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp)
                )
                is PdfRenderState.Ready -> {
                    ZoomableBitmap(
                        bitmap = state.bitmap,
                        contentDescription = stringResource(
                            R.string.document_pdf_page_description,
                            state.pageIndex + 1
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val ready = renderState as? PdfRenderState.Ready
        if (ready != null && ready.pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { requestedPage = (ready.pageIndex - 1).coerceAtLeast(0) },
                    enabled = ready.pageIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.document_previous_page))
                }

                Text(
                    stringResource(
                    R.string.document_page_number,
                    ready.pageIndex + 1,
                    ready.pageCount
                ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                IconButton(
                    onClick = {
                        requestedPage = (ready.pageIndex + 1)
                            .coerceAtMost(ready.pageCount - 1)
                    },
                    enabled = ready.pageIndex < ready.pageCount - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.document_next_page))
                }
            }
        }
    }
}

private fun renderPdfPage(path: String, requestedPage: Int): PdfRenderState {
    return try {
        val file = File(path)
        if (!file.isFile) {
            return PdfRenderState.Error("O arquivo deste documento não foi encontrado.")
        }

        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) {
                    return PdfRenderState.Error("Este PDF não contém páginas.")
                }

                val pageIndex = requestedPage.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(pageIndex).use { page ->
                    val scale = minOf(
                        1440f / page.width.toFloat(),
                        2048f / page.height.toFloat(),
                        2.5f
                    ).coerceAtLeast(0.1f)
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    PdfRenderState.Ready(
                        bitmap = bitmap,
                        pageCount = renderer.pageCount,
                        pageIndex = pageIndex
                    )
                }
            }
        }
    } catch (_: Exception) {
        PdfRenderState.Error("Não foi possível abrir este PDF.")
    }
}
