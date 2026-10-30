@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.exams

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenipane.orbit.data.PastPaper
import com.shreenipane.orbit.data.PastPapers
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val FILTERS = listOf("All", "CAT 1", "CAT 2", "FAT")
private fun matches(p: PastPaper, filter: Int) = when (filter) {
    1 -> p.exam.contains("CAT-1")
    2 -> p.exam.contains("CAT-2")
    3 -> p.exam.contains("FAT")
    else -> true
}

/** Past papers for one course, from CodeChef-VIT's archive. Tap one to read it in the app. */
@Composable
fun PapersSheet(title: String, code: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var papers by remember { mutableStateOf<List<PastPaper>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<PastPaper?>(null) }

    LaunchedEffect(code) {
        runCatching { PastPapers.forCourse(code) }
            .onSuccess { papers = it }
            .onFailure { error = it.message ?: "Couldn't reach the papers archive." }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pastel.Page) {
        Column(Modifier.fillMaxHeight(0.9f).padding(start = 16.dp, end = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Pastel.Ink, maxLines = 2)
            Text("$code · from papers.codechefvit.com", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
            Spacer(Modifier.height(12.dp))
            Segmented(FILTERS, filter, { filter = it })
            Spacer(Modifier.height(12.dp))
            val list = papers
            when {
                error != null -> Text(error.orEmpty(), color = Pastel.InkMuted, modifier = Modifier.padding(8.dp))
                list == null -> Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp, color = Pastel.Ink)
                    Spacer(Modifier.width(10.dp)); Text("Finding papers…", color = Pastel.Ink)
                }
                else -> {
                    val shown = list.filter { matches(it, filter) }
                    if (shown.isEmpty()) {
                        Text("No papers for this yet.", color = Pastel.InkMuted, modifier = Modifier.padding(8.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().brutal(Pastel.Card), contentPadding = PaddingValues(vertical = 2.dp)) {
                            items(shown.size) { i ->
                                val p = shown[i]
                                if (i > 0) RowDivider()
                                ListRow(
                                    "${p.exam} · ${p.year}",
                                    listOf(p.semester.removeSuffix(" Semester"), p.slot.takeIf { it.isNotBlank() }?.let { "Slot $it" },
                                        p.campus.takeIf { !it.equals("Vellore", true) }, if (p.answerKey) "Answer key" else null)
                                        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                                    trailing = { if (PastPapers.cached(ctx, p) != null) Tag("Saved", Good) },
                                    chevron = true,
                                    onClick = { open = p },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    open?.let { p -> PaperViewer(p) { open = null } }
}

/** Full-screen reader: downloads once, renders pages with Android's PDF renderer, pinch to zoom. */
@Composable
private fun PaperViewer(paper: PastPaper, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paper.id) {
        runCatching { PastPapers.file(ctx, paper) }
            .onSuccess { file = it }
            .onFailure { error = it.message ?: "Couldn't download the paper." }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = file
        if (uri == null || f == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } ?: error("no stream")
                }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "Saved" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Pastel.Page, contentColor = Pastel.Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Pastel.Ink) }
                    Column(Modifier.weight(1f)) {
                        Text("${paper.exam} · ${paper.year}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(paper.subject, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val f = file
                    IconButton(enabled = f != null, onClick = { runCatching { save.launch(PastPapers.fileName(paper)) } }) {
                        Icon(Icons.Rounded.Download, "Save to phone", tint = if (f != null) Pastel.Ink else Pastel.InkMuted)
                    }
                    IconButton(enabled = f != null, onClick = {
                        if (f == null) return@IconButton
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        runCatching {
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share paper"))
                        }
                    }) { Icon(Icons.Rounded.Share, "Share", tint = if (f != null) Pastel.Ink else Pastel.InkMuted) }
                }
                val f = file
                when {
                    error != null -> Text(error.orEmpty(), color = Pastel.InkMuted, modifier = Modifier.padding(24.dp))
                    f == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Pastel.Ink)
                            Spacer(Modifier.height(10.dp)); Text("Downloading…")
                        }
                    }
                    else -> PdfPages(f)
                }
            }
        }
    }
}

/** Renders a PDF's pages one by one as you scroll. Pinch with two fingers to zoom and move sideways. */
@Composable
private fun PdfPages(file: File) {
    val holder = remember(file) {
        runCatching {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fd to PdfRenderer(fd)
        }.getOrNull()
    }
    val lock = remember { Mutex() }
    DisposableEffect(holder) {
        onDispose { runCatching { holder?.second?.close(); holder?.first?.close() } }
    }
    if (holder == null) {
        Text("This file couldn't be opened. Try saving it and opening it in another app.", color = Pastel.InkMuted,
            modifier = Modifier.padding(24.dp))
        // The file may be a broken download; drop it so the next try downloads again.
        LaunchedEffect(file) { runCatching { file.delete() } }
        return
    }
    val renderer = holder.second
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF555B61))) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LazyColumn(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                val maxX = (scale - 1f) * size.width / 2f
                                offsetX = (offsetX + event.calculatePan().x).coerceIn(-maxX, maxX)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(renderer.pageCount) { index ->
                val page by produceState<Bitmap?>(null, index, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        lock.withLock {
                            runCatching {
                                val pg = renderer.openPage(index)
                                try {
                                    // Render a bit sharper than the screen so zooming in stays readable.
                                    val w = (widthPx * 1.5f).toInt().coerceIn(600, 2400)
                                    val h = (w.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                } finally {
                                    pg.close()
                                }
                            }.getOrNull()
                        }
                    }
                }
                val bmp = page
                if (bmp == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.width(24.dp), strokeWidth = 2.dp, color = Color.Gray)
                    }
                } else {
                    Image(bmp.asImageBitmap(), contentDescription = "Page ${index + 1}",
                        modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
                        contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}
