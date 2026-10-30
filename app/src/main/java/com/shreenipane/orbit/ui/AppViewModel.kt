package com.shreenipane.orbit.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreenipane.orbit.core.ShareCode
import com.shreenipane.orbit.core.model.CampusInfo
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DigitalAssignment
import com.shreenipane.orbit.core.model.OverrideKind
import com.shreenipane.orbit.core.model.PlannedCourse
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.parse.CalendarParsers
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.data.AppSettings
import com.shreenipane.orbit.data.Campus
import com.shreenipane.orbit.data.Repository
import com.shreenipane.orbit.data.SessionExpiredException
import com.shreenipane.orbit.widget.OrbitWidget
import com.shreenipane.orbit.work.Work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class UiState(
    val loading: Boolean = true,
    val snapshot: Snapshot? = null,
    val refreshing: Boolean = false,
    /** True when we have nothing to show and must sign in. */
    val showLogin: Boolean = false,
    /** True when cached data is showing but VTOP wants a fresh sign-in. */
    val sessionExpired: Boolean = false,
    val message: String? = null,
    val campus: Campus = Campus.VELLORE,
    val reminders: List<Reminder> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /** Registration number in the fingerprint locker, if a login is saved. */
    val savedLogin: String? = null,
    /** Calendar class group the user chose; null = automatic (General (Flexible)). */
    val classGroup: String? = null,
    val daUpload: DaUpload = DaUpload.Idle,
    val overrides: List<ClassOverride> = emptyList(),
    val planner: List<PlannedCourse> = emptyList(),
    val studyPlan: List<com.shreenipane.orbit.core.model.StudyItem> = emptyList(),
    val campusInfo: CampusInfo? = null,
    val campusLoading: Boolean = false,
)

/** Progress of a DA upload, shown in the DA sheet. */
sealed class DaUpload {
    object Idle : DaUpload()
    data class Working(val title: String) : DaUpload()
    data class NeedOtp(val title: String) : DaUpload()
    data class Done(val message: String) : DaUpload()
    data class Failed(val message: String) : DaUpload()
}

class AppViewModel(private val app: Application, private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow(
        UiState(campus = repo.campus, settings = repo.settings, savedLogin = repo.vault.savedRegNo, classGroup = repo.classGroupId),
    )
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            val cached = repo.cached()
            _state.update {
                it.copy(
                    loading = false, snapshot = cached, showLogin = cached == null,
                    reminders = repo.reminders(), friends = repo.friends(),
                    overrides = repo.overrides(), planner = repo.planner(), studyPlan = repo.studyPlan(), campusInfo = repo.campusInfo(),
                )
            }
            if (cached != null) refresh(silent = true)
        }
    }

    // ---------- session ----------

    fun setCampus(c: Campus) {
        repo.campus = c
        _state.update { it.copy(campus = c) }
    }

    /** The login WebView reached VTOP's logged-in page. */
    fun onSignedIn() {
        _state.update { it.copy(showLogin = false, sessionExpired = false) }
        Work.ensurePeriodic(app, repo.settings) // restarts the keep-alive if it stopped
        refresh()
    }

    fun newLogin() = repo.newLogin()

    suspend fun debugReport(): java.io.File = repo.debugReport()

    fun requestSignIn() = _state.update { it.copy(showLogin = true) }

    fun cancelSignIn() = _state.update { it.copy(showLogin = it.snapshot == null) }

    fun selectSemester(id: String) {
        repo.pinnedSemesterId = id
        refresh(semesterId = id)
    }

    fun refresh(silent: Boolean = false, semesterId: String? = repo.pinnedSemesterId) {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, message = null) }
            try {
                val snap = repo.refresh(semesterId)
                _state.update { it.copy(snapshot = snap, refreshing = false, sessionExpired = false) }
                Work.planAlerts(app, snap, repo.settings, _state.value.overrides, null)
                OrbitWidget.update(app, snap, _state.value.overrides)
            } catch (e: SessionExpiredException) {
                _state.update {
                    it.copy(refreshing = false, sessionExpired = true, showLogin = it.snapshot == null)
                }
            } catch (e: IOException) {
                _state.update { it.copy(refreshing = false, message = if (silent) null else "Couldn't reach VTOP. Showing saved data.") }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, message = e.message ?: "Something went wrong") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            Work.planAlerts(app, null, repo.settings)
            _state.update { it.copy(snapshot = null, showLogin = true, sessionExpired = false, savedLogin = null) }
        }
    }

    // ---------- credential locker ----------

    val vault get() = repo.vault

    /** Called after a fingerprint-confirmed save or removal so the UI reflects the locker. */
    fun lockerChanged() = _state.update { it.copy(savedLogin = repo.vault.savedRegNo) }

    fun removeSavedLogin() {
        repo.vault.clear()
        lockerChanged()
    }

    // ---------- calendar ----------

    /** null = automatic. Refetches so classes-left counts use the new group. */
    fun setClassGroup(name: String?) {
        repo.classGroupId = name
        _state.update { it.copy(classGroup = name) }
        refresh()
    }

    // ---------- settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val prev = _state.value.settings
        val next = transform(prev)
        if (next == prev) return
        repo.settings = next
        _state.update { it.copy(settings = next) }
        if (next.backgroundSync != prev.backgroundSync || next.keepSignedIn != prev.keepSignedIn) Work.ensurePeriodic(app, next)
        if (next.weeklyReport != prev.weeklyReport) Work.ensurePeriodic(app, next)
        if (next.classAlerts != prev.classAlerts || next.classAlertLeadMinutes != prev.classAlertLeadMinutes ||
            next.examAlerts != prev.examAlerts
        ) Work.planAlerts(app, _state.value.snapshot, next, _state.value.overrides, null)
    }

    // ---------- reminders & assignments ----------

    fun upsertReminder(r: Reminder) = editReminders { list ->
        Work.scheduleReminder(app, r)
        list.filterNot { it.id == r.id } + r
    }

    fun toggleDone(r: Reminder) = upsertReminder(r.copy(done = !r.done))

    fun deleteReminder(r: Reminder) = editReminders { list ->
        Work.cancelReminder(app, r.id)
        list.filterNot { it.id == r.id }
    }

    private fun editReminders(edit: (List<Reminder>) -> List<Reminder>) {
        val next = edit(_state.value.reminders).sortedBy { it.dueAtMillis }
        _state.update { it.copy(reminders = next) }
        viewModelScope.launch { repo.saveReminders(next) }
    }

    // ---------- digital assignment upload ----------

    private val daTypes = mapOf(
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    private fun setUpload(u: DaUpload) = _state.update { it.copy(daUpload = u) }

    fun resetDaUpload() = setUpload(DaUpload.Idle)

    /** Reads the picked file, checks VTOP's limits (PDF/Word/Excel, 4 MB), and uploads it. */
    fun uploadDa(da: DigitalAssignment, uri: Uri) {
        viewModelScope.launch {
            setUpload(DaUpload.Working(da.title))
            try {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    val cr = app.contentResolver
                    val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "assignment.pdf"
                    name to (cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0))
                }
                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = daTypes[ext]
                when {
                    mime == null -> setUpload(DaUpload.Failed("VTOP only takes PDF, Word or Excel files (.pdf, .doc, .docx, .xls, .xlsx)."))
                    bytes.isEmpty() -> setUpload(DaUpload.Failed("That file is empty."))
                    bytes.size > 4 * 1024 * 1024 -> setUpload(DaUpload.Failed("That file is %.1f MB; VTOP's limit is 4 MB.".format(bytes.size / 1048576.0)))
                    else -> handleUpload(da.title, repo.uploadDa(da, name, bytes, mime))
                }
            } catch (e: SessionExpiredException) {
                setUpload(DaUpload.Failed("Your VTOP session ended. Sign in, then upload again."))
                _state.update { it.copy(sessionExpired = true) }
            } catch (e: Exception) {
                setUpload(DaUpload.Failed(e.message ?: "Upload failed."))
            }
        }
    }

    fun submitDaOtp(title: String, otp: String) {
        viewModelScope.launch {
            setUpload(DaUpload.Working(title))
            try {
                handleUpload(title, repo.verifyDaOtp(otp))
            } catch (e: Exception) {
                setUpload(DaUpload.Failed(e.message ?: "OTP check failed."))
            }
        }
    }

    private fun handleUpload(title: String, r: CalendarParsers.UploadResult) {
        when (r) {
            CalendarParsers.UploadResult.Success -> { setUpload(DaUpload.Done("$title uploaded.")); refresh(silent = true) }
            CalendarParsers.UploadResult.OtpRequired -> setUpload(DaUpload.NeedOtp(title))
            is CalendarParsers.UploadResult.Failed -> setUpload(DaUpload.Failed(r.message))
        }
    }

    // ---------- class overrides (cancelled / extra) ----------

    fun addOverride(o: ClassOverride) = editOverrides { list -> list.filterNot { it.id == o.id } + o }

    fun removeOverride(o: ClassOverride) = editOverrides { list -> list.filterNot { it.id == o.id } }

    /** Toggles "cancelled" for one class on one date. */
    fun toggleCancelled(date: java.time.LocalDate, s: ClassSession) {
        val existing = _state.value.overrides.firstOrNull {
            it.kind == OverrideKind.CANCELLED && it.date == date.toString() && it.courseCode == s.courseCode &&
                it.isLab == s.isLab && it.start == s.start
        }
        if (existing != null) removeOverride(existing)
        else addOverride(ClassOverride(System.currentTimeMillis(), date.toString(), OverrideKind.CANCELLED,
            s.courseCode, s.courseTitle, s.isLab, s.start, s.end, s.venue))
    }

    private fun editOverrides(edit: (List<ClassOverride>) -> List<ClassOverride>) {
        val today = java.time.LocalDate.now().minusDays(30).toString()
        val next = edit(_state.value.overrides).filter { it.date >= today }.sortedBy { it.date + it.start }
        _state.update { it.copy(overrides = next) }
        viewModelScope.launch {
            repo.saveOverrides(next)
            Work.planAlerts(app, _state.value.snapshot, repo.settings, next, null)
            OrbitWidget.update(app, _state.value.snapshot, next)
        }
    }

    // ---------- your study plan ----------

    fun upsertStudy(i: com.shreenipane.orbit.core.model.StudyItem) = editStudy { list -> list.filterNot { it.id == i.id } + i }
    fun removeStudy(i: com.shreenipane.orbit.core.model.StudyItem) = editStudy { list -> list.filterNot { it.id == i.id } }
    private fun editStudy(edit: (List<com.shreenipane.orbit.core.model.StudyItem>) -> List<com.shreenipane.orbit.core.model.StudyItem>) {
        val next = edit(_state.value.studyPlan).sortedWith(compareBy({ it.date }, { it.id }))
        _state.update { it.copy(studyPlan = next) }
        viewModelScope.launch { repo.saveStudyPlan(next) }
    }

    // ---------- FFCS planner ----------

    fun upsertPlanned(c: PlannedCourse) = editPlanner { list -> list.filterNot { it.id == c.id } + c }
    fun removePlanned(c: PlannedCourse) = editPlanner { list -> list.filterNot { it.id == c.id } }
    private fun editPlanner(edit: (List<PlannedCourse>) -> List<PlannedCourse>) {
        val next = edit(_state.value.planner)
        _state.update { it.copy(planner = next) }
        viewModelScope.launch { repo.savePlanner(next) }
    }

    // ---------- campus pages ----------

    private fun sessionEnded(e: Exception): Boolean {
        if (e is SessionExpiredException) { _state.update { it.copy(sessionExpired = true) }; return true }
        return false
    }

    fun refreshCampus() {
        if (_state.value.campusLoading) return
        viewModelScope.launch {
            _state.update { it.copy(campusLoading = true) }
            try {
                val c = repo.refreshCampus()
                _state.update { it.copy(campusInfo = c, campusLoading = false) }
                Work.planAlerts(app, _state.value.snapshot, repo.settings, _state.value.overrides, c)
            } catch (e: Exception) {
                sessionEnded(e)
                _state.update { it.copy(campusLoading = false, message = if (e is SessionExpiredException) null else e.message) }
            }
        }
    }

    /** Runs a one-off VTOP lookup; failures come back as a message instead of throwing. */
    suspend fun <T> lookup(block: suspend Repository.() -> T): kotlin.Result<T> = try {
        kotlin.Result.success(repo.block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        sessionEnded(e)
        kotlin.Result.failure(if (e is SessionExpiredException) Exception("VTOP session ended. Sign in and try again.") else e)
    }

    // ---------- export & backup ----------

    suspend fun icsFile(): java.io.File? = repo.icsFile()
    suspend fun backupJson(): String = repo.backupJson()

    fun restore(text: String) {
        viewModelScope.launch {
            try {
                val b = repo.restore(text)
                _state.update {
                    it.copy(reminders = repo.reminders(), friends = repo.friends(), overrides = repo.overrides(),
                        planner = repo.planner(), studyPlan = repo.studyPlan(), settings = repo.settings,
                        message = "Restored ${b.reminders.size} reminders, ${b.friends.size} friends, ${b.planner.size} planned courses.")
                }
                repo.reminders().forEach { Work.scheduleReminder(app, it) }
            } catch (e: Exception) {
                _state.update { it.copy(message = "That doesn't look like an Orbit backup.") }
            }
        }
    }

    // ---------- friends ----------

    /** My timetable as a share code, named with my alias (or reg no). */
    fun myShareCode(): String? {
        val snap = _state.value.snapshot ?: return null
        val name = _state.value.settings.alias.ifBlank { snap.regNo }
        return ShareCode.encode(ShareCode.Payload(name, snap.semester?.name.orEmpty(), snap.timetable.sessions))
    }

    /** Returns an error message, or null on success. */
    fun importFriend(text: String): String? {
        val p = ShareCode.decode(text) ?: return "That doesn't look like an Orbit share code."
        if (p.sessions.isEmpty()) return "That code has no classes in it."
        val friend = Friend(p.name, p.semester, p.sessions, System.currentTimeMillis())
        val next = _state.value.friends.filterNot { it.name == p.name } + friend
        _state.update { it.copy(friends = next) }
        viewModelScope.launch { repo.saveFriends(next) }
        return null
    }

    fun removeFriend(f: Friend) {
        val next = _state.value.friends.filterNot { it.name == f.name }
        _state.update { it.copy(friends = next) }
        viewModelScope.launch { repo.saveFriends(next) }
    }

    companion object {
        fun factory(app: Application, repo: Repository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(app, repo) }
        }
    }
}
