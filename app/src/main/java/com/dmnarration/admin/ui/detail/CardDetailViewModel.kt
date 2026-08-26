package com.dmnarration.admin.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.CardAccessNotEnabledException
import com.dmnarration.admin.domain.CardDetail
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
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val board: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardDetailUiState())
    val state: StateFlow<CardDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun start(cardId: String) {
        if (loadedId == cardId) return
        loadedId = cardId
        load(cardId, initial = true)
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
            board.cardDetail(cardId)
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
