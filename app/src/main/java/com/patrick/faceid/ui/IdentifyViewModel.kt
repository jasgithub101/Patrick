package com.patrick.faceid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrick.faceid.app.EngineStatus
import com.patrick.faceid.app.IdentifyOutcome
import com.patrick.faceid.app.RecognitionEngine
import com.patrick.faceid.face.RgbImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IdentifyUiState(
    val busy: Boolean = false,
    val stage: String? = null,
    val result: IdentifyOutcome? = null,
    val status: EngineStatus? = null,
    val error: String? = null,
)

class IdentifyViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(IdentifyUiState())
    val state: StateFlow<IdentifyUiState> = _state.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            runCatching { RecognitionEngine.get(getApplication()).status() }
                .onSuccess { _state.value = _state.value.copy(status = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun identify(image: RgbImage) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, result = null, error = null, stage = "Detect")
        viewModelScope.launch {
            runCatching { RecognitionEngine.get(getApplication()).identify(image) }
                .onSuccess { _state.value = _state.value.copy(busy = false, stage = null, result = it) }
                .onFailure { _state.value = _state.value.copy(busy = false, stage = null, error = it.message) }
        }
    }

    fun clear() {
        _state.value = _state.value.copy(result = null, error = null)
    }
}

data class PeopleUiState(
    val people: List<Triple<String, String, Int>> = emptyList(),
    val loading: Boolean = true,
)

class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { RecognitionEngine.get(getApplication()).people() }
                .onSuccess { rows ->
                    _state.value = PeopleUiState(
                        people = rows.map { (person, count) ->
                            Triple(person.displayName, person.dummyAbhaId, count)
                        },
                        loading = false,
                    )
                }
                .onFailure { _state.value = PeopleUiState(loading = false) }
        }
    }
}
