package com.aistudio.zoya.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PermissionsViewModel : ViewModel() {
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted

    fun completeOnboarding() {
        _onboardingCompleted.value = true
    }
}
