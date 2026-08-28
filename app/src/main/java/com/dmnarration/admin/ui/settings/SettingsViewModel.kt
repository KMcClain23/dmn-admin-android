package com.dmnarration.admin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.StudioSettingsRepository
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.domain.SiteSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.WRITE_REFUSED_MESSAGE
import com.dmnarration.admin.domain.serverRefusalMessage
import com.dmnarration.admin.ui.describeDataFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: SiteSettings? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val capabilities: Capabilities = Capabilities.of(UserRole.EDITOR),
    /**
     * Per-key write state, so two fields saving at once cannot overwrite each
     * other's outcome. A single `saving`/`error` pair on the screen would make
     * a refused rate look like a saved capacity.
     */
    val writes: Map<String, FieldWrite<String>> = emptyMap(),
) {
    fun writeFor(key: String): FieldWrite<String> = writes[key] ?: FieldWrite.Idle
}

/**
 * Settings, now writable.
 *
 * Its own ViewModel rather than more state on the board's: the board loads
 * settings because its cards need rates, and a write path bolted onto that
 * would make every settings save a reason to re-render the board.
 *
 * The write discipline is Stage 2's, unchanged. What is new is that the RULE
 * being enforced lives in the database — `check_site_setting()` — so a refusal
 * arrives as a sentence rather than as a code this app has to interpret. The
 * phone displays what the server said, which is what makes the phone and the
 * web agree by construction rather than by two people wording a message the
 * same way.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: StudioSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var started = false

    /** Told when a rate changes, so the board can re-read what it costs things with. */
    var onSettingSaved: (() -> Unit)? = null

    fun start(role: UserRole) {
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        if (started) return
        started = true
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.value = _state.value.copy(loading = initial, refreshing = !initial)
        viewModelScope.launch {
            settings.loadAll().fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        settings = it,
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        settings = null,
                        loading = false,
                        refreshing = false,
                        error = describeDataFailure(
                            t = t,
                            tag = TAG,
                            logMessage = "settings load failed",
                            refused = "Settings are not visible to this account.",
                            revoked = "Your session is no longer allowed to read settings. " +
                                "Try signing out and in again.",
                            generic = "Could not load settings. Pull down to try again.",
                        ),
                    )
                },
            )
        }
    }

    /**
     * Save one setting.
     *
     * The optimistic apply is deliberately NOT done to `settings` here. Unlike a
     * board card, these values are re-read and re-parsed as a whole object, and
     * a half-applied local edit would have to reconstruct `SiteSettings` from a
     * string this app has not validated — reimplementing on the phone the rule
     * that was just moved into the database. Instead the field shows `Saving`
     * with its own pending text, and the server's answer replaces it. The
     * rollback is therefore exact by construction: the field falls back to
     * whatever `settings` still holds, which was never modified.
     */
    fun save(key: String, value: String) {
        if (!_state.value.capabilities.canEdit) return
        if (_state.value.writeFor(key) is FieldWrite.Saving) return

        setWrite(key, FieldWrite.Saving(value))

        viewModelScope.launch {
            settings.updateSetting(key, value).fold(
                onSuccess = { stored ->
                    if (stored == null) {
                        // Zero rows: RLS refused. Success-shaped, and not a save.
                        setWrite(key, FieldWrite.Refused)
                        // A refusal says this session's permissions changed, not
                        // just this row, so re-read rather than trust what is held.
                        load(initial = false)
                    } else {
                        setWrite(key, FieldWrite.Saved(stored))
                        load(initial = false)
                        onSettingSaved?.invoke()
                    }
                },
                onFailure = { t ->
                    // The database's own sentence when it refused the value, and
                    // this app's wording only when it was not the database talking.
                    val fromServer = serverRefusalMessage(t)
                    setWrite(
                        key,
                        FieldWrite.Failed(
                            message = fromServer ?: describeDataFailure(
                                t = t,
                                tag = TAG,
                                logMessage = "settings write failed",
                                refused = WRITE_REFUSED_MESSAGE,
                                revoked = "Your session is no longer allowed to change settings. " +
                                    "Try signing out and in again.",
                                generic = "Could not save that. Try again.",
                            ),
                            fromServer = fromServer != null,
                        ),
                    )
                },
            )
        }
    }

    /** Clear a field's outcome, when the user edits it again. */
    fun clearWrite(key: String) = setWrite(key, FieldWrite.Idle)

    private fun setWrite(key: String, write: FieldWrite<String>) {
        _state.value = _state.value.copy(writes = _state.value.writes + (key to write))
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
