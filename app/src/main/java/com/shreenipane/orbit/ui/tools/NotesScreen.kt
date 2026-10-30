package com.shreenipane.orbit.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenipane.orbit.core.model.CourseNote
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Pastel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Notes per course, with an optional photo (e.g. the board). Kept on the phone only. */
@Composable
fun NotesScreen(snap: Snapshot, notes: List<CourseNote>, vm: AppViewModel) {
    var editing by remember { mutableStateOf<CourseNote?>(null) }
    var viewing by remember { mutableStateOf<File?>(null) }
    val courses = snap.timetable.courses.map { it.code to it.title }.distinctBy { it.first }

    OrbitScreen(title = "Notes") {
        section(action = "+ Note" to {
            val first = courses.firstOrNull()
            editing = CourseNote(System.currentTimeMillis(), first?.first.orEmpty(), first?.second.orEmpty(), "")
        }) {
            Text("Quick notes and photos for each course. They stay on this phone.", style = MaterialTheme.typography.bodySmall,
                color = Pastel.InkMuted, modifier = Modifier.padding(16.dp))
        }
        if (notes.isEmpty()) {
            item { EmptyNote("No notes yet", "Tap + Note to add one.", Modifier.padding(top = 12.dp)) }
        }
        notes.groupBy { it.courseTitle.ifBlank { it.courseCode } }.forEach { (course, list) ->
            section(header = course) {
                list.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    NoteRow(n, vm, onOpen = { editing = n }, onPhoto = { viewing = it })
                }
            }
        }
    }

    editing?.let { n ->
        NoteDialog(n, isNew = notes.none { it.id == n.id }, courses = courses, vm = vm,
            onSave = { vm.upsertNote(it); editing = null }, onDelete = { vm.removeNote(n); editing = null }, onDismiss = { editing = null })
    }
    viewing?.let { f -> PhotoViewer(f) { viewing = null } }
}

@Composable
private fun NoteRow(n: CourseNote, vm: AppViewModel, onOpen: () -> Unit, onPhoto: (File) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(n.text.ifBlank { "Photo" }, style = MaterialTheme.typography.bodyLarge, color = Pastel.Ink, maxLines = 4)
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.createdAt)), style = MaterialTheme.typography.bodySmall,
                color = Pastel.InkMuted)
        }
        n.photo?.let { name ->
            val file = vm.notePhotoFile(name)
            Spacer(Modifier.width(12.dp))
            Thumb(file, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).clickable { onPhoto(file) })
        }
    }
}

/** Decodes a small copy of the photo off the main thread. */
@Composable
private fun Thumb(file: File, modifier: Modifier, maxPx: Int = 200) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / s > maxPx) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), contentDescription = "Note photo", modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier)
}

@Composable
private fun PhotoViewer(file: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Thumb(file, Modifier.fillMaxSize().padding(8.dp), maxPx = 2000)
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(
    initial: CourseNote, isNew: Boolean, courses: List<Pair<String, String>>, vm: AppViewModel,
    onSave: (CourseNote) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initial.courseCode) }
    var title by remember { mutableStateOf(initial.courseTitle) }
    var text by remember { mutableStateOf(initial.text) }
    var photo by remember { mutableStateOf(initial.photo) }
    var pick by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch { vm.importNotePhoto(uri)?.let { photo = it }; busy = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pastel.Page,
        title = { Text(if (isNew) "New note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    TextButton(onClick = { pick = true }) { Text(title.ifBlank { "Pick a course" }, maxLines = 1) }
                    DropdownMenu(expanded = pick, onDismissRequest = { pick = false }) {
                        courses.forEach { (c, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { code = c; title = t; pick = false }) }
                    }
                }
                OutlinedTextField(text, { text = it.take(4000) }, label = { Text("Note") }, minLines = 3, maxLines = 8,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }) { Text(if (busy) "Adding…" else if (photo == null) "Add photo" else "Change photo") }
                    if (photo != null) TextButton(onClick = { photo = null }) { Text("Remove photo", color = Bad) }
                }
                photo?.let { Thumb(vm.notePhotoFile(it), Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (text.isNotBlank() || photo != null) && code.isNotBlank(), onClick = {
                // Drop the old photo file if it was replaced or removed.
                if (initial.photo != null && initial.photo != photo) runCatching { vm.notePhotoFile(initial.photo).delete() }
                onSave(initial.copy(courseCode = code, courseTitle = title, text = text.trim(), photo = photo))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
