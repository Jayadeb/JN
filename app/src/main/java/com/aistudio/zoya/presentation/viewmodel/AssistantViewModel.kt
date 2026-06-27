package com.aistudio.zoya.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.zoya.data.gemini.LiveSessionManager
import com.aistudio.zoya.domain.model.AssistantState
import com.aistudio.zoya.util.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val liveSessionManager: LiveSessionManager,
    private val soundManager: SoundManager
) : ViewModel() {

    val state: StateFlow<AssistantState> = liveSessionManager.state
    val volume: StateFlow<Float> = liveSessionManager.volume
    val pitch: StateFlow<Float> = liveSessionManager.pitch

    private var lastState: AssistantState = AssistantState.Idle

    init {
        viewModelScope.launch {
            state.collect { newState ->
                if (newState != lastState) {
                    lastState = newState
                }
            }
        }
    }

    private val _testingMode = MutableStateFlow(false)
    val testingMode: StateFlow<Boolean> = _testingMode

    private val _testState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val testState: StateFlow<AssistantState> = _testState

    private val _testVolume = MutableStateFlow(0f)
    val testVolume: StateFlow<Float> = _testVolume

    fun toggleTestingMode() {
        _testingMode.value = !_testingMode.value
    }

    fun setTestState(newState: AssistantState) {
        _testState.value = newState
    }

    fun setTestVolume(newVolume: Float) {
        _testVolume.value = newVolume
    }

    fun setPitch(newPitch: Float) {
        liveSessionManager.setPitch(newPitch)
    }

    fun playTestSound() {
        soundManager.playActivationSound()
    }

    fun startListening() {
        liveSessionManager.startSession()
    }

    fun stopListening() {
        liveSessionManager.stopSession()
    }

    fun sendText(text: String) {
        liveSessionManager.sendText(text)
    }
}
