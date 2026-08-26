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

    // NOTE: there is deliberately no `today` field here. Holding one would
    // freeze every date, urgency colour and bucket at whatever day the app was
    // launched, and a board left open overnight would quietly keep saying
    // "tomorrow". It is derived inside load() and inside the filter re-apply,
    // and pull-to-refresh is what corrects a board left open.
    private val _state = MutableStateFlow(BoardUiState(today = currentDay()))
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    private var started = false

    fun start(role: UserRole) {
        this.role = role
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        // The caller keys this on the role, so it arrives once per role; the
        // flag only stops a re-load when the composable is recreated around a
        // surviving ViewModel, as it is on rotation.
        if (started) return
        started = true
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

            board.loadBoard(role)
                .onSuccess { cards ->
                    allCards = cards
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        settings = settings,
                    )
                    reproject()
                }
                .onFailure { t ->
                    // Deliberately keeps allCards and the buckets. A failed
                    // refresh is the app not knowing anything new, not the
                    // board becoming empty — dropping twenty good cards
                    // because a pull timed out is a worse answer than showing
                    // them with a note. There is no security argument for
                    // clearing either: a revoked session is not a failure, it
                    // is a successful fetch that returns nothing (RLS answers
                    // 200 with an empty list), which the success path below
                    // handles by replacing state honestly. The boundary is the
                    // server, and someone who has lost access can simply not
                    // pull.
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        settings = settings,
                        error = describe(t),
                    )
                }
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
