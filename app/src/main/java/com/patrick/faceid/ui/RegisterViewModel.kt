package com.patrick.faceid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrick.faceid.app.RecognitionEngine
import com.patrick.faceid.db.FaceRepository
import com.patrick.faceid.face.PipelineResult
import com.patrick.faceid.face.RgbImage
import com.patrick.faceid.registration.RegistrationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Guided capture prompts. Registration deliberately collects several views of the same person,
 * because one photo generalises poorly across pose and glasses.
 */
private val CAPTURE_PROMPTS = listOf(
    "Look straight at the camera",
    "Turn your head slightly left",
    "Turn your head slightly right",
    "Neutral expression",
    "Natural expression, or with glasses on or off",
)

data class RegisterUiState(
    val employeeName: String = "",
    val samplesTaken: Int = 0,
    val samplesRequired: Int = 5,
    val prompt: String = CAPTURE_PROMPTS.first(),
    val lastSampleQuality: Float? = null,
    val statusMessage: String? = null,
    val statusOk: Boolean = true,
    val busy: Boolean = false,
    val savedPersonName: String? = null,
    val savedDummyAbhaId: String? = null,
    val duplicate: RegistrationResult.PossibleDuplicate? = null,
    val error: String? = null,
) {
    val canCapture: Boolean get() = !busy && samplesTaken < samplesRequired
    val canSave: Boolean get() = !busy && samplesTaken >= samplesRequired && employeeName.isNotBlank()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val samples = mutableListOf<FaceRepository.StoredEmbedding>()

    init {
        viewModelScope.launch {
            val required = RecognitionEngine.get(getApplication()).settings().registration.imagesPerPerson
            _state.value = _state.value.copy(samplesRequired = required)
        }
    }

    fun onNameChanged(name: String) {
        _state.value = _state.value.copy(employeeName = name)
    }

    /** Runs the real pipeline. A poor-quality sample is reported and NOT stored. */
    fun captureSample(image: RgbImage) {
        if (!_state.value.canCapture) return
        _state.value = _state.value.copy(busy = true, statusMessage = null)
        viewModelScope.launch {
            runCatching { RecognitionEngine.get(getApplication()).captureSample(image) }
                .onSuccess { result ->
                    when (result) {
                        is PipelineResult.Embedded -> {
                            samples += FaceRepository.StoredEmbedding(
                                vector = result.embedding,
                                captureRole = roleFor(samples.size),
                            )
                            val taken = samples.size
                            _state.value = _state.value.copy(
                                busy = false,
                                samplesTaken = taken,
                                lastSampleQuality = result.quality,
                                prompt = CAPTURE_PROMPTS.getOrElse(taken) { CAPTURE_PROMPTS.last() },
                                statusMessage = "Sample " + taken + " accepted",
                                statusOk = true,
                            )
                        }

                        is PipelineResult.Rejected -> _state.value = _state.value.copy(
                            busy = false,
                            statusMessage = result.message,
                            statusOk = false,
                        )
                    }
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
        }
    }

    fun save(force: Boolean = false) {
        val current = _state.value
        if (!current.canSave && !force) return
        _state.value = current.copy(busy = true, duplicate = null)
        viewModelScope.launch {
            runCatching {
                RecognitionEngine.get(getApplication()).register(current.employeeName.trim(), samples.toList(), force)
            }.onSuccess { result ->
                when (result) {
                    is RegistrationResult.Registered -> _state.value = _state.value.copy(
                        busy = false,
                        savedPersonName = current.employeeName.trim(),
                        savedDummyAbhaId = result.dummyAbhaId,
                    )

                    is RegistrationResult.PossibleDuplicate -> _state.value =
                        _state.value.copy(busy = false, duplicate = result)
                }
            }.onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
        }
    }

    fun dismissDuplicate() {
        _state.value = _state.value.copy(duplicate = null)
    }

    fun reset() {
        samples.clear()
        _state.value = RegisterUiState(samplesRequired = _state.value.samplesRequired)
    }

    private fun roleFor(index: Int): String = when (index) {
        0 -> "frontal"
        1 -> "left"
        2 -> "right"
        3 -> "neutral"
        else -> "natural"
    }
}
