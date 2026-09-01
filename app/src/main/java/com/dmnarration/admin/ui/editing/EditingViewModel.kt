package com.dmnarration.admin.ui.editing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.EditorAssignment
import com.dmnarration.admin.domain.NeedsMe
import com.dmnarration.admin.domain.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Editing tab's data: what is waiting on you, and the books it is on.
 *
 * ── THE SCOPE COMES FROM THE SERVER, NOT FROM HERE ─────────────────────────
 *
 * `pickupsNeedingMe()` already answers "which rows are mine" per role — an
 * admin's sent pickups, an editor's returned ones — and `editorAssignments()`
 * already answers "whose book is this". Neither is re-filtered below. A second
 * filtering mechanism in the client is how the website and the phone end up
 * disagreeing about what she is supposed to be looking at, and it is the thing
 * this screen was explicitly not to add.
 *
 * The board read is the same `loadBoard(role)` every other screen uses, so an
 * editor gets `board_for_editor()` here exactly as she does on the board.
 */
data class EditingUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refused: Boolean = false,
    val role: UserRole = UserRole.UNKNOWN,
    /** Whose session this is, for telling her books from everyone else's. */
    val myId: String? = null,
    val needsMe: List<NeedsMe> = emptyList(),
    /** Books in editing. Everything for an admin; the same list for an editor. */
    val books: List<BoardCard> = emptyList(),
    val assignments: List<EditorAssignment> = emptyList(),
    /** Set when a claim or unclaim is refused, so the reason reaches the screen. */
    val claimError: String? = null,
) {
    /** The holder of a book, or null when nobody has claimed it. */
    fun holderOf(cardId: String): EditorAssignment? = assignments.firstOrNull { it.cardId == cardId }

    fun isMine(cardId: String): Boolean = myId != null && holderOf(cardId)?.editorId == myId

    /** Somebody outside is editing it, so it is not hers to claim. */
    fun isExternal(cardId: String): Boolean = holderOf(cardId)?.editedExternally == true
}

@HiltViewModel
class EditingViewModel @Inject constructor(
    private val repo: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditingUiState())
    val state: StateFlow<EditingUiState> = _state.asStateFlow()

    private var role: UserRole = UserRole.UNKNOWN
    private var started = false

    fun start(role: UserRole, userId: String?) {
        this.role = role
        _state.update { it.copy(role = role, myId = userId) }
        if (started) return
        started = true
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        // UNKNOWN reads nothing. A role whose permissions cannot be established
        // is not a role to guess a screen for — the same rule the board follows.
        if (role == UserRole.UNKNOWN) {
            _state.update { it.copy(loading = false, refused = true, error = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }

            val board = repo.loadBoard(role)
            val needs = repo.pickupsNeedingMe()
            val assigned = repo.editorAssignments()

            val failure = board.exceptionOrNull() ?: needs.exceptionOrNull() ?: assigned.exceptionOrNull()
            if (failure != null) {
                /*
                  A FAILED READ KEEPS THE LAST GOOD LISTS.

                  Emptying them would render as "nothing is waiting on you",
                  which is a claim about her workload made by a read that did not
                  happen. The banner says something went wrong; the rows below it
                  stay as they were.
                */
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        refused = failure is com.dmnarration.admin.data.BoardAccessNotEnabledException,
                        error = failure.message ?: "Could not load editing.",
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    refused = false,
                    error = null,
                    needsMe = needs.getOrDefault(emptyList()),
                    books = board.getOrDefault(emptyList()).filter { c -> c.status == "editing" },
                    assignments = assigned.getOrDefault(emptyList()),
                )
            }
        }
    }

    fun claim(cardId: String) = claimOrRelease(cardId) { repo.claimCard(cardId) }

    fun unclaim(cardId: String) = claimOrRelease(cardId) { repo.releaseCard(cardId) }

    /**
     * A REFUSAL IS SHOWN, NOT SWALLOWED.
     *
     * `claim_card_for_editing` raises when somebody else holds the book, and the
     * message names them. That message is the only way the person tapping would
     * ever find out; without it the button appears to do nothing, which is the
     * failure shape this project keeps finding.
     */
    private fun claimOrRelease(cardId: String, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(claimError = null) }
            val result = action()
            val e = result.exceptionOrNull()
            if (e != null) {
                _state.update { it.copy(claimError = e.message ?: "That did not work.") }
                return@launch
            }
            load(initial = false)
        }
    }

    fun clearClaimError() = _state.update { it.copy(claimError = null) }

    /** Re-read after a write made on the book pane, so counts do not go stale. */
    fun onBookChanged() = load(initial = false)
}
