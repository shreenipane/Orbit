package com.shreenipane.orbit.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shreenipane.orbit.core.ShareCode
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.data.AppSettings
import com.shreenipane.orbit.data.Campus
import com.shreenipane.orbit.data.Repository
import com.shreenipane.orbit.data.SessionExpiredException
import com.shreenipane.orbit.work.Work
import kotlinx.coroutines.flow.MutableStateFlow
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
)

class AppViewModel(private val app: Application, private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow(UiState(campus = repo.campus, settings = repo.settings))
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            val cached = repo.cached()
            _state.update {
                it.copy(
                    loading = false, snapshot = cached, showLogin = cached == null,
                    reminders = repo.reminders(), friends = repo.friends(),
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
        refresh()
    }

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
                Work.planAlerts(app, snap, repo.settings)
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
            _state.update { it.copy(snapshot = null, showLogin = true, sessionExpired = false) }
        }
    }

    // ---------- settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val prev = _state.value.settings
        val next = transform(prev)
        if (next == prev) return
        repo.settings = next
        _state.update { it.copy(settings = next) }
        if (next.backgroundSync != prev.backgroundSync) Work.ensurePeriodic(app, next)
        if (next.classAlerts != prev.classAlerts || next.classAlertLeadMinutes != prev.classAlertLeadMinutes ||
            next.examAlerts != prev.examAlerts
        ) Work.planAlerts(app, _state.value.snapshot, next)
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
