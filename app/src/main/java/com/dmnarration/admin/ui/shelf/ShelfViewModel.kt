package com.dmnarration.admin.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.ReleasedCounts
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.WriteOutcome
import com.dmnarration.admin.domain.applyOptimistic
import com.dmnarration.admin.domain.reconcileWrite
import com.dmnarration.admin.domain.releasedCounts
import com.dmnarration.admin.domain.stillArchived
import com.dmnarration.admin.domain.visibleReleased
import com.dmnarration.admin.ui.describeDataFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the two shelf screens show.
 *
 * The two lists carry their own loading flags and their own errors because they
 * come from two independent reads that fail independently. One shared `error`
 * would let a working Released screen wear the Archive's failure, and — worse —
 * would let a failed Archive read render as an empty archive, which on this
 * screen means "nothing has been archived". That is the same collapse as bug 6,
 * one screen along.
 */
data class ShelfState(
    val releasedLoading: Boolean = true,
    val archivedLoading: Boolean = true,
    val refreshing: Boolean = false,
    /** Released and not archived — what the list shows. */
    val released: List<ReleasedBook> = emptyList(),
    /** Both populations, so a caller has to say which one it means. */
    val counts: ReleasedCounts = ReleasedCounts(0, 0),
    val archived: List<ArchivedCard> = emptyList(),
    val releasedError: String? = null,
    val archivedError: String? = null,
    /** Set by a write, cleared by the next one. Never doubles as a load error. */
    val writeError: String? = null,
    val capabilities: Capabilities = Capabilities.of(UserRole.EDITOR),
) {
    /**
     * True only when the read succeeded and returned nothing.
     *
     * The empty state is reassurance — "nothing is archived" — and it must not
     * be reachable by failing. A screen that says nothing is archived when it
     * simply could not find out is telling Dean his archive is empty.
     */
    val archiveGenuinelyEmpty: Boolean
        get() = !archivedLoading && archivedError == null && archived.isEmpty()

    val releasedGenuinelyEmpty: Boolean
        get() = !releasedLoading && releasedError == null && released.isEmpty()
}

/**
 * Released and Archive, and the one write Stage 6 adds.
 *
 * Separate from `BoardViewModel` because these are separate reads with separate
 * failures, and folding them into the board's state would have made a failed
 * archive read look like a board problem. `onRestored` is how a successful
 * un-archive tells the board to re-fetch: the restored card belongs on it again,
 * and this ViewModel has no business reaching into the board's list to put it
 * there.
 */
@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val board: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShelfState())
    val state: StateFlow<ShelfState> = _state.asStateFlow()

    /** Every archived card, restored ones included, so a rollback is verbatim. */
    private var allArchived: List<ArchivedCard> = emptyList()
    private val inFlight = mutableSetOf<String>()
    private var started = false
    private var stale = false

    /** Called after a successful restore, to put the card back on the board. */
    var onRestored: (() -> Unit)? = null

    fun start(role: UserRole) {
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        if (started) return
        started = true
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    /**
     * Something on the board changed, so these lists are no longer current.
     *
     * Marked rather than re-fetched: the shelf is usually not on screen when
     * this happens, and a write should not spend two round trips updating lists
     * nobody is looking at. [onShown] pays the cost at the moment it matters.
     */
    fun markStale() {
        stale = true
    }

    /** Called when a shelf screen becomes visible. Re-reads only if it has to. */
    fun onShown() {
        if (!stale) return
        stale = false
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        _state.value = _state.value.copy(
            releasedLoading = initial,
            archivedLoading = initial,
            refreshing = !initial,
        )
        // Two launches, not one: a slow or failing Archive read must not hold up
        // Released, and neither may take the other down.
        viewModelScope.launch {
            board.released().fold(
                onSuccess = { rows ->
                    _state.value = _state.value.copy(
                        released = visibleReleased(rows),
                        counts = releasedCounts(rows),
                        releasedLoading = false,
                        releasedError = null,
                    )
                },
                onFailure = { t ->
                    // The list is emptied deliberately. Leaving the previous rows
                    // under an error message shows data no longer known to be true
                    // beside a message saying it could not be read.
                    _state.value = _state.value.copy(
                        released = emptyList(),
                        counts = ReleasedCounts(0, 0),
                        releasedLoading = false,
                        releasedError = describeDataFailure(
                            t = t,
                            tag = TAG,
                            logMessage = "released load failed",
                            refused = "Released books are not visible to this account.",
                            revoked = "Your session is no longer allowed to read released " +
                                "books. Try signing out and in again.",
                            generic = "Could not load released books. Pull down to try again.",
                        ),
                    )
                },
            )
            settleRefreshing()
        }
        viewModelScope.launch {
            board.archived().fold(
                onSuccess = { rows ->
                    allArchived = rows
                    _state.value = _state.value.copy(
                        archived = stillArchived(rows),
                        archivedLoading = false,
                        archivedError = null,
                    )
                },
                onFailure = { t ->
                    allArchived = emptyList()
                    _state.value = _state.value.copy(
                        archived = emptyList(),
                        archivedLoading = false,
                        archivedError = describeDataFailure(
                            t = t,
                            tag = TAG,
                            logMessage = "archive load failed",
                            refused = "The archive is not visible to this account.",
                            revoked = "Your session is no longer allowed to read the archive. " +
                                "Try signing out and in again.",
                            generic = "Could not load the archive. Pull down to try again.",
                        ),
                    )
                },
            )
            settleRefreshing()
        }
    }

    /** The spinner stops when both reads have answered, not when the first does. */
    private fun settleRefreshing() {
        val s = _state.value
        if (!s.releasedLoading && !s.archivedLoading) {
            _state.value = s.copy(refreshing = false)
        }
    }

    /**
     * Put a card back, with Stage 2's write discipline rather than a copy of it.
     *
     * The optimistic apply clears `archivedAt` locally, which drops the row out
     * of `stillArchived` immediately; the card stays in `allArchived` so a
     * refusal restores it exactly as it was rather than as a card reconstructed
     * from what the app thinks it should be.
     */
    fun unarchive(cardId: String) {
        if (!_state.value.capabilities.canEdit) return
        if (cardId in inFlight) return

        val (optimistic, pending) =
            applyOptimistic(allArchived, cardId) { it.copy(archivedAt = null) }
        if (pending == null) return

        inFlight += cardId
        allArchived = optimistic
        _state.value = _state.value.copy(
            archived = stillArchived(allArchived),
            writeError = null,
        )

        viewModelScope.launch {
            val outcome = board.unarchive(cardId).fold(
                // A row means the un-archive landed. Zero rows means the server
                // accepted the statement and changed nothing, which is RLS
                // refusing this row — success-shaped, and not a save.
                onSuccess = { row -> row?.let { WriteOutcome.Saved(it) } ?: WriteOutcome.Refused },
                onFailure = {
                    WriteOutcome.Failed(
                        describeDataFailure(
                            t = it,
                            tag = TAG,
                            logMessage = "unarchive failed",
                            refused = "The archive is not visible to this account.",
                            revoked = "Your session is no longer allowed to change this card. " +
                                "Try signing out and in again.",
                            generic = "Could not restore that card. Try again.",
                        )
                    )
                },
            )

            val reduction = reconcileWrite(allArchived, pending, outcome)
            allArchived = reduction.cards
            inFlight -= cardId
            _state.value = _state.value.copy(
                archived = stillArchived(allArchived),
                writeError = reduction.error,
            )
            // The card belongs on the board again, and only the board can put it there.
            if (outcome is WriteOutcome.Saved) onRestored?.invoke()
            if (reduction.refresh) load(initial = false)
        }
    }

    fun dismissWriteError() {
        _state.value = _state.value.copy(writeError = null)
    }

    private companion object {
        const val TAG = "ShelfViewModel"
    }
}
