package com.dmnarration.admin.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.CardAccessNotEnabledException
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.WRITE_REFUSED_MESSAGE
import com.dmnarration.admin.domain.serverRefusalMessage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val detail: CardDetail? = null,
    val error: String? = null,
    /**
     * The server refused this read. Distinct from `error != null`, which also covers
     * a timeout — and distinct from "no such card", which is what a direct select
     * would have made it indistinguishable from.
     */
    val refused: Boolean = false,
    /** The id matched nothing. Not a refusal, and not an error. */
    val missing: Boolean = false,
    val capabilities: Capabilities = Capabilities.of(UserRole.EDITOR),
    /**
     * Per-COLUMN write state. One card has two dozen editable fields and two of
     * them can be in flight at once; a single saving/error pair on the screen
     * would show a refused word count as a saved title.
     */
    val writes: Map<String, FieldWrite<String>> = emptyMap(),
) {
    fun writeFor(column: String): FieldWrite<String> = writes[column] ?: FieldWrite.Idle
}

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val board: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardDetailUiState())
    val state: StateFlow<CardDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    /** Held so a refresh routes the same way the first load did. */
    private var role: UserRole = UserRole.UNKNOWN

    fun start(cardId: String, role: UserRole) {
        this.role = role
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        if (loadedId == cardId) return
        loadedId = cardId
        load(cardId, initial = true)
    }

    /**
     * Save ONE field.
     *
     * A DELIBERATE DIVERGENCE from the web, which PUTs the whole form on every
     * save. A whole-form write cannot tell "sent unchanged" from "changed", and
     * the page rule already had to work around exactly that with `is distinct
     * from` — CardEditModal ships words_recorded in every payload, so testing
     * presence rather than change would clear current_page each time Dean edited
     * a title. A single-column patch has no such ambiguity: what arrives is what
     * was meant, and the trigger can trust it.
     *
     * The optimistic value is NOT written into `detail`. The server's row is the
     * truth and a trigger may change more than was asked — writing current_page
     * moves words_recorded, and writing words_recorded clears current_page — so
     * the field shows `Saving` with its own pending text and the reload replaces
     * it. Rollback is therefore exact by construction: `detail` was never
     * modified, so the field falls back to what it already held.
     */
    fun save(column: String, raw: String) {
        if (!_state.value.capabilities.canEdit) return
        if (_state.value.writeFor(column) is FieldWrite.Saving) return
        val cardId = loadedId ?: return

        setWrite(column, FieldWrite.Saving(raw))

        viewModelScope.launch {
            val patch = buildJsonObject {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) put(column, JsonNull) else put(column, JsonPrimitive(trimmed))
            }
            board.updateCard(cardId, patch).fold(
                onSuccess = { row ->
                    if (row == null) {
                        // Zero rows: RLS refused. Success-shaped, and not a save.
                        setWrite(column, FieldWrite.Refused)
                        // A refusal is about this session's permissions, not this
                        // field, so re-read rather than trust what is held.
                        load(cardId, initial = false)
                    } else {
                        setWrite(column, FieldWrite.Saved(raw.trim()))
                        load(cardId, initial = false)
                    }
                },
                onFailure = { t ->
                    // The database's own sentence when it refused the value, and
                    // this app's wording only when it was not the database talking.
                    val fromServer = serverRefusalMessage(t)
                    Log.w(TAG, "card field write failed: $column", t)
                    setWrite(
                        column,
                        FieldWrite.Failed(
                            message = fromServer ?: describeWriteFailure(t),
                            fromServer = fromServer != null,
                        ),
                    )
                },
            )
        }
    }

    /** Clear a field's outcome, when the user edits it again. */
    fun clearWrite(column: String) = setWrite(column, FieldWrite.Idle)

    private fun setWrite(column: String, write: FieldWrite<String>) {
        _state.value = _state.value.copy(writes = _state.value.writes + (column to write))
    }

    private fun describeWriteFailure(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("permission denied", ignoreCase = true) ||
                message.contains("42501") -> WRITE_REFUSED_MESSAGE
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                t is java.io.IOException -> "No connection. Try again."
            else -> "Could not save that. Try again."
        }
    }

    fun refresh() {
        loadedId?.let { load(it, initial = false) }
    }

    private fun load(cardId: String, initial: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = initial,
                refreshing = !initial,
                error = null,
            )
            board.cardDetail(cardId, role)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        detail = detail ?: _state.value.detail,
                        missing = detail == null,
                        refused = false,
                        error = null,
                    )
                }
                .onFailure { t ->
                    val refused = t is CardAccessNotEnabledException
                    Log.w(TAG, "card detail failed", t)
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        // A refusal withdraws what is on screen. The server has said
                        // this session may not read this card; continuing to show
                        // what it last read contradicts that answer.
                        detail = if (refused) null else _state.value.detail,
                        refused = refused,
                        error = describe(t),
                    )
                }
        }
    }

    private fun describe(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            t is CardAccessNotEnabledException ->
                "Board access is not enabled for this account yet."
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Failed to connect", ignoreCase = true) ||
                t is java.io.IOException ->
                "No connection. Pull down to try again."
            else -> "Could not load this card. Pull down to try again."
        }
    }

    private companion object {
        const val TAG = "CardDetailViewModel"
    }
}
