@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.campus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.BiometricEntry
import com.shreenipane.orbit.core.model.Faculty
import com.shreenipane.orbit.core.model.KeyValue
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.UiState
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * VTOP's "everything else": announcements, your proctor and HOD/Dean,
 * a faculty finder, fees, and the hostel biometric log. Announcements,
 * proctor, HOD/Dean and fees are cached; the rest is looked up on demand.
 */
@Composable
fun CampusScreen(state: UiState, vm: AppViewModel) {
    val info = state.campusInfo
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Faculty>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Pair<Faculty, List<KeyValue>?>?>(null) }
    var bioDate by remember { mutableStateOf(LocalDate.now()) }
    var bio by remember { mutableStateOf<List<BiometricEntry>?>(null) }
    var bioLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stale = info == null || System.currentTimeMillis() - info.fetchedAtMillis > 3 * 3600_000L
        if (stale) vm.refreshCampus()
    }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true; lookupError = null
        scope.launch {
            vm.lookup { facultySearch(query.trim()) }.onSuccess { results = it }.onFailure { lookupError = it.message }
            searching = false
        }
    }

    fun loadBio(d: LocalDate) {
        bioDate = d; bioLoading = true; lookupError = null
        scope.launch {
            vm.lookup { biometric(d) }.onSuccess { bio = it }.onFailure { lookupError = it.message; bio = null }
            bioLoading = false
        }
    }

    OrbitScreen(
        title = "Campus",
        subtitle = if (state.campusLoading) "Updating from VTOP…" else "From VTOP",
        refreshing = state.campusLoading,
        onRefresh = vm::refreshCampus,
    ) {
        item {
            Segmented(listOf("News", "People", "Fees", "Hostel"), tab, { tab = it }, Modifier.padding(top = 4.dp))
        }
        lookupError?.let { msg ->
            item { Text(msg, color = Bad, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        }

        when (tab) {
            0 -> {
                val groups = info?.announcements.orEmpty().groupBy { it.category }
                if (groups.isEmpty()) {
                    section { ListRow(if (state.campusLoading) "Loading…" else "No announcements", "Pull down to check VTOP again.") }
                }
                groups.forEach { (cat, list) ->
                    section(header = cat) {
                        Rows(list) { a -> ListRow(a.text, a.link?.let { "Has an attachment on VTOP" }) }
                    }
                }
            }
            1 -> {
                section(header = "Your proctor") { KeyValues(info?.proctor.orEmpty(), state.campusLoading) }
                section(header = "HOD & Dean") { KeyValues(info?.hodDean.orEmpty(), state.campusLoading) }
                section(header = "Find a faculty member", footer = "Search by name; tap a result for cabin, e-mail and open hours.") {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            query, { query = it }, label = { Text("Faculty name") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        BrutalButton(if (searching) "Searching…" else "Search", { search() }, Modifier.fillMaxWidth(),
                            fill = Pastel.Sky, enabled = !searching && query.isNotBlank())
                    }
                    results?.let { list ->
                        if (list.isEmpty()) ListRow("No matches", null)
                        Rows(list) { f ->
                            ListRow(f.name, "${f.designation} · ${f.school}", chevron = true, onClick = {
                                detail = f to null
                                scope.launch {
                                    vm.lookup { facultyDetail(f.empId) }
                                        .onSuccess { detail = f to it }
                                        .onFailure { lookupError = it.message; detail = null }
                                }
                            })
                        }
                    }
                }
            }
            2 -> {
                val pending = info?.pending.orEmpty()
                section(header = "Due", footer = if (state.settings.feeReminders) "You'll get a reminder two days before and on the due date." else null) {
                    if (pending.isEmpty()) ListRow(if (state.campusLoading) "Loading…" else "Nothing due", null)
                    Rows(pending) { p ->
                        ListRow(p.head, "Due ${p.dueDate} · ref ${p.reference}" + if (p.fine.isNotBlank() && p.fine != "0") " · fine ₹${p.fine}" else "",
                            trailing = { Tag("₹${p.total}", Warn) })
                    }
                }
                section(header = "Paid") {
                    val receipts = info?.receipts.orEmpty()
                    if (receipts.isEmpty()) ListRow(if (state.campusLoading) "Loading…" else "No receipts", null)
                    Rows(receipts) { r -> ListRow("₹${r.amount}", "${r.date} · receipt ${r.number}") }
                }
            }
            else -> {
                section(header = "Biometric log", footer = "Your hostel in/out punches for the chosen day.") {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrutalButton("‹", { loadBio(bioDate.minusDays(1)) }, fill = Pastel.Card)
                        Text(bioDate.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        BrutalButton("›", { loadBio(bioDate.plusDays(1)) }, fill = Pastel.Card, enabled = bioDate.isBefore(LocalDate.now()))
                    }
                    val entries = bio
                    when {
                        bioLoading -> ListRow("Loading…", null)
                        entries == null -> ListRow("Load today's punches", null, chevron = true, onClick = { loadBio(bioDate) })
                        entries.isEmpty() -> ListRow("No punches that day", null)
                        else -> Rows(entries) { b -> ListRow(b.time, b.location) }
                    }
                }
            }
        }
    }

    detail?.let { (f, kv) ->
        ModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Pastel.Page) {
            Column(Modifier.padding(start = 24.dp, end = 28.dp, bottom = 32.dp)) {
                Text(f.name, style = MaterialTheme.typography.headlineMedium)
                Text("${f.designation} · ${f.school}", style = MaterialTheme.typography.bodyMedium, color = Pastel.InkMuted)
                Spacer(Modifier.height(16.dp))
                if (kv == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp, color = Pastel.Ink)
                        Spacer(Modifier.width(10.dp)); Text("Loading…")
                    }
                } else {
                    kv.forEach { (k, v) ->
                        Text(k, style = MaterialTheme.typography.labelLarge, color = Pastel.InkMuted, modifier = Modifier.padding(top = 8.dp))
                        Text(v, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValues(list: List<KeyValue>, loading: Boolean) {
    if (list.isEmpty()) {
        ListRow(if (loading) "Loading…" else "Nothing from VTOP yet", if (loading) null else "Pull down to try again.")
        return
    }
    list.forEachIndexed { i, kv ->
        if (i > 0) com.shreenipane.orbit.ui.components.RowDivider()
        ListRow(kv.value, kv.key)
    }
}
