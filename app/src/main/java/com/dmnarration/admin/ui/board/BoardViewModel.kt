package com.dmnarration.admin.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardAccessNotEnabledException
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.StudioSettingsRepository
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.DEFAULT_STUDIO_SETTINGS
import com.dmnarration.admin.domain.DateFilter
import com.dmnarration.admin.domain.PipelineBucket
import com.dmnarration.admin.domain.ProductionSubgroup
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.WriteOutcome
import com.dmnarration.admin.domain.applyOptimistic
import com.dmnarration.admin.domain.reconcileWrite
import com.dmnarration.admin.domain.bucketPipeline
import com.dmnarration.admin.domain.bucketProduction
import com.dmnarration.admin.domain.currentDay
import com.dmnarration.admin.domain.isPipeline
import com.dmnarration.admin.domain.isProduction
import com.dmnarration.admin.domain.passesDateFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

data class BoardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val capabilities: Capabilities = Capabilities.of(UserRole.UNKNOWN),
    val settings: StudioSettings = DEFAULT_STUDIO_SETTINGS,
    /**
     * The day every date on screen was measured against.
     *
     * Carried in the state rather than read inside a composable so the whole
     * board is drawn against one consistent day, and recomputed on every load
     * so it cannot go stale — see `currentDay()` for why that matters.
     */
    val today: LocalDate,
    val dateFilter: DateFilter? = null,
    val pipeline: Map<PipelineBucket, List<BoardCard>> = emptyMap(),
    val production: Map<ProductionSubgroup, List<BoardCard>> = emptyMap(),
    val pipelineCount: Int = 0,
    val productionCount: Int = 0,
    /**
     * The server has said this session may not read the board.
     *
     * Distinct from `error != null`, which also covers a timeout. In this state
     * the screen shows the message and nothing else: no counts, no filter
     * chips, no tabs. "Pipeline (0)" is honest about the viewport and answers a
     * question nobody asked — it implies there IS a pipeline that happens to be
     * empty, which is the exact ambiguity bug 6 was made of, and the person
     * seeing it will be an editor on every launch until F3 with no way to know
     * the zero describes the viewport rather than their work.
     */
    val refused: Boolean = false,
) {
    val isEmpty: Boolean get() = pipelineCount == 0 && productionCount == 0
}

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val board: BoardRepository,
    private val studio: StudioSettingsRepository,
) : ViewModel() {

    private var role: UserRole = UserRole.UNKNOWN
    private var allCards: List<BoardCard> = emptyList()

    /** Cards with a write in flight, so a double tap cannot race itself. */
    private val inFlight = mutableSetOf<String>()

    // NOTE: there is deliberately no `today` field here. Holding one would
    // freeze every date, urgency colour and bucket at whatever day the app was
    // launched, and a board left open overnight would quietly keep saying
    // "tomorrow". It is derived inside load() and inside the filter re-apply,
    // and pull-to-refresh is what corrects a board left open.
    private val _state = MutableStateFlow(BoardUiState(today = currentDay()))
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    /**
     * The role this board was loaded for, or null before the first load.
     *
     * Was a bare `started` boolean, which is the second fault found alongside
     * bug 6: it made the first call the only call, so a role change that WAS
     * noticed still would not re-load. Keying on the role instead means
     * rotation — the case the flag existed for — still skips the fetch, because
     * the role is unchanged, while an actual change does not.
     */
    private var loadedForRole: UserRole? = null

    fun start(role: UserRole) {
        this.role = role
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        if (loadedForRole == role) return
        loadedForRole = role
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun setDateFilter(filter: DateFilter) {
        val next = if (_state.value.dateFilter == filter) null else filter
        _state.value = _state.value.copy(dateFilter = next)
        reproject()
    }

    private fun load(initial: Boolean) {
        if (!role.isRecognised) {
            _state.value = _state.value.copy(
                loading = false,
                error = "This session has no recognised role, so no board can be shown.",
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = initial,
                refreshing = !initial,
                error = null,
            )

            // Only when the session may read them. An editor has no policy
            // granting select on site_settings, and asking would produce an
            // error that reads as a bug rather than a rule.
            val settings = if (_state.value.capabilities.canViewStudioSettings) {
                studio.load().getOrElse { DEFAULT_STUDIO_SETTINGS }
            } else {
                DEFAULT_STUDIO_SETTINGS
            }

            board.loadBoard()
                .onSuccess { cards ->
                    allCards = cards
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        refused = false,
                        settings = settings,
                        // Restores what a refusal withdrew. Only an admin gets
                        // rows out of board_for_session(), so a successful read
                        // is the server itself saying this session still is
                        // one. Recovery arrives by pull-to-refresh rather than
                        // start(), because nothing re-resolves the role
                        // mid-session — without this the cards would come back
                        // read-only until the app was restarted.
                        capabilities = Capabilities.of(role),
                    )
                    reproject()
                }
                .onFailure { t ->
                    // The two failures are not the same failure, and the old
                    // code's claim that "a revoked session is a successful
                    // fetch returning nothing" was exactly bug 6 written down:
                    // it treated the refusal as a legitimate empty board.
                    //
                    // A transport fault keeps the cards. The app not knowing
                    // anything new is not the board becoming empty, and
                    // dropping twenty good cards because a pull timed out is a
                    // worse answer than showing them with a note.
                    //
                    // A refusal clears them, and withdraws the gestures with
                    // them. The server has said this session may not read the
                    // board; continuing to show what it last read, with buttons
                    // that still offer to change it, contradicts that answer.
                    val refused = t is BoardAccessNotEnabledException
                    if (refused) {
                        allCards = emptyList()
                        loadedForRole = null // so a restored role re-fetches
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        settings = settings,
                        error = describe(t),
                        refused = refused,
                        capabilities = if (refused) {
                            Capabilities.of(UserRole.UNKNOWN)
                        } else {
                            _state.value.capabilities
                        },
                    )
                    if (refused) reproject()
                }
        }
    }

    /**
     * Toggle a card's First-15, optimistically.
     *
     * Applies locally, writes, then reconciles against what the server actually
     * returned — never against whether the call threw. An RLS-refused update
     * comes back as a successful statement affecting zero rows, so "no
     * exception" is not evidence of a save.
     *
     * The patch carries only `first_15_complete`. `updated_at` is a trigger's
     * job and this must not touch it; if a rule the database owns ever appears
     * in this file, the migration is wrong.
     */
    fun toggleFirst15(cardId: String) {
        if (!_state.value.capabilities.canEdit) return
        if (cardId in inFlight) return

        val (optimistic, pending) = applyOptimistic(allCards, cardId) { card ->
            card.copy(first15Complete = !card.first15Complete)
        }
        if (pending == null) return

        inFlight += cardId
        allCards = optimistic
        _state.value = _state.value.copy(error = null)
        reproject()

        viewModelScope.launch {
            val outcome = board
                .updateCard(cardId, buildJsonObject {
                    put("first_15_complete", optimistic.first { it.id == cardId }.first15Complete)
                })
                .fold(
                    // A row means saved; null means zero rows, which is RLS
                    // refusing this row rather than anything going wrong.
                    onSuccess = { row -> row?.let(WriteOutcome::Saved) ?: WriteOutcome.Refused },
                    onFailure = { WriteOutcome.Failed(describe(it)) },
                )

            val reduction = reconcileWrite(allCards, pending, outcome)
            allCards = reduction.cards
            inFlight -= cardId
            _state.value = _state.value.copy(error = reduction.error)
            reproject()
            // A refusal says this session's view has changed, not just one row.
            if (reduction.refresh) load(initial = false)
        }
    }

    /**
     * Re-bucket what is already loaded.
     *
     * `today` is read here, not captured once, so a chip tap after midnight
     * re-measures rather than reusing the day the app started on.
     */
    private fun reproject() {
        val today = currentDay()
        val filter = _state.value.dateFilter
        val visible = allCards.filter { passesDateFilter(it, filter, today) }
        val pipelineCards = visible.filter(::isPipeline)
        val productionCards = visible.filter(::isProduction)

        _state.value = _state.value.copy(
            today = today,
            pipeline = bucketPipeline(pipelineCards, today),
            production = bucketProduction(productionCards),
            pipelineCount = pipelineCards.size,
            productionCount = productionCards.size,
        )
    }

    /**
     * One sentence for the person; the detail goes to the log.
     *
     * This used to interpolate the exception message straight onto the screen.
     * supabase-kt's message carries the request that failed — full URL with
     * query string, and the headers, including Authorization and apikey. They
     * arrive truncated, but that truncation is the library's choice rather than
     * ours, and this is the surface an editor is eventually meant to see. An
     * error a person reads gets a sentence and a way forward; the object it came
     * from is for whoever is debugging.
     */
    private fun describe(t: Throwable): String {
        Log.w(TAG, "board load failed", t)
        val message = t.message.orEmpty()
        return when {
            t is BoardAccessNotEnabledException ->
                "Board access is not enabled for this account yet."
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Failed to connect", ignoreCase = true) ||
                t is java.io.IOException ->
                "No connection. Pull down to try again."
            message.contains("permission denied", ignoreCase = true) ||
                message.contains("JWT", ignoreCase = true) ->
                "Your session is no longer allowed to read the board. Try signing out and in again."
            else -> "Could not load the board. Pull down to try again."
        }
    }

    private companion object {
        const val TAG = "BoardViewModel"
    }
}
