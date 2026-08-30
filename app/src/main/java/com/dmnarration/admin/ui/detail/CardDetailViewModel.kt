package com.dmnarration.admin.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.CardAccessNotEnabledException
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
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
    /** Pickups on THIS card. Empty until the read returns. */
    val pickups: List<Pickup> = emptyList(),
    /** Whose session this is, so the UI only offers actions the server will allow. */
    val userId: String? = null,
    /** A pickup write that failed. Separate from `error`, which is the card read. */
    val pickupError: String? = null,
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
    private var userId: String? = null

    fun start(cardId: String, role: UserRole, userId: String? = null) {
        this.role = role
        this.userId = userId
        _state.value = _state.value.copy(userId = userId)
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
            // THE FOUR FINANCIAL COLUMNS HAVE NO DIRECT WRITE PATH. Their UPDATE
            // grant was revoked from `authenticated`, so a patch naming one of
            // them now fails with permission denied rather than saving. They go
            // through the admin-gated definer function instead; every other
            // column keeps the direct path deliberately.
            if (column in FINANCIAL_COLUMNS) {
                board.setCardFinancial(cardId, column, raw.trim()).fold(
                    onSuccess = {
                        setWrite(column, FieldWrite.Saved(raw.trim()))
                        // The function returns nothing, so the screen learns what
                        // was actually stored by re-reading — including anything a
                        // trigger changed.
                        load(cardId, initial = false)
                    },
                    onFailure = { t ->
                        val fromServer = serverRefusalMessage(t)
                        Log.w(TAG, "financial field write failed: $column", t)
                        setWrite(
                            column,
                            FieldWrite.Failed(
                                message = fromServer ?: describeWriteFailure(t),
                                fromServer = fromServer != null,
                            ),
                        )
                    },
                )
                return@launch
            }

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
                    launch { loadPickups() }
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
        /**
         * The columns with no direct write path. Listed here rather than
         * inferred from the field's group, because the GRANT is what decides
         * this and the grant names these four — a Money-group field that is not
         * one of them would still write directly.
         */
        val FINANCIAL_COLUMNS = setOf(
            "pfh_rate", "payment_type", "narrator_share_percent", "royalty_split_percent",
        )

        const val TAG = "CardDetailViewModel"
    }

    // ── Editing progress and pickups ────────────────────────────────────────
    //
    // Every one of these is a SECURITY DEFINER function with a role gate. The
    // screen decides which buttons to draw; the SERVER decides what happens. A
    // refusal is surfaced, never swallowed — an action that silently does
    // nothing is the failure this project keeps paying for.

    fun setProgress(edited: Int?, total: Int?) = write {
        board.setEditingProgress(requireNotNull(loadedId), edited, total)
    }

    fun markComplete(complete: Boolean) = write {
        board.setEditingComplete(requireNotNull(loadedId), complete)
    }

    fun raisePickup(
        chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedTo: String,
    ) = write {
        board.createPickup(
            requireNotNull(loadedId), chapter, timestampAt, kind, said, shouldBe, note, assignedTo,
        ).map { }
    }

    fun editPickup(
        id: String, chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedTo: String,
    ) = write {
        board.updateOwnDraftPickup(id, chapter, timestampAt, kind, said, shouldBe, note, assignedTo)
    }

    fun deletePickup(id: String) = write { board.deleteOwnDraftPickup(id) }

    /** draft -> sent for one chapter. No email in this stage; E3 hangs it here. */
    fun sendChapter(chapter: String) = write {
        board.sendChapterPickups(requireNotNull(loadedId), chapter).map { }
    }

    fun resolvePickup(id: String, status: PickupStatus) = write {
        board.resolvePickup(id, status)
    }

    /**
     * Run a write, then re-read.
     *
     * The re-read is not optimism about the write: it is how the screen learns
     * what the server actually stored, including anything a trigger changed. An
     * optimistic local update would show the value the client hoped for.
     */
    private fun write(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(pickupError = null)
            block()
                .onSuccess { loadedId?.let { load(it, initial = false) } }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        pickupError = t.message ?: "That did not go through.",
                    )
                }
        }
    }

    private suspend fun loadPickups() {
        board.pickups(role)
            .onSuccess { all ->
                _state.value = _state.value.copy(
                    pickups = all.filter { it.cardId == loadedId },
                )
            }
            // A failed pickup read must not blank the card. The card is the
            // subject; pickups are an addition to it.
            .onFailure { /* leave the previous list in place */ }
    }
}
