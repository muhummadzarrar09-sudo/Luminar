// app/src/main/java/com/luminar/reader/presentation/settings/SettingsViewModel.kt
package com.luminar.reader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminar.reader.BuildConfig
import com.luminar.reader.data.local.datastore.UserPreferencesRepository
import com.luminar.reader.data.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedTheme: AppTheme = AppTheme.DARK_AMOLED,
    val keepScreenOn: Boolean = true,
    val volumeButtonsPageTurn: Boolean = true,
    val ollamaBaseUrl: String = "http://192.168.1.1:11434",
    val ollamaModel: String = "deepseek-r1:7b",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val isLoading: Boolean = true
)

sealed interface SettingsEvent {
    data class ThemeSelected(val theme: AppTheme) : SettingsEvent
    data class KeepScreenOnChanged(val enabled: Boolean) : SettingsEvent
    data class VolumeButtonsPageTurnChanged(val enabled: Boolean) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        selectedTheme = preferences.selectedTheme,
                        keepScreenOn = preferences.keepScreenOn,
                        volumeButtonsPageTurn = preferences.volumeButtonsPageTurn,
                        ollamaBaseUrl = preferences.ollamaBaseUrl,
                        ollamaModel = preferences.ollamaModel,
                        appVersion = BuildConfig.VERSION_NAME,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeSelected -> setTheme(event.theme)
            is SettingsEvent.KeepScreenOnChanged -> setKeepScreenOn(event.enabled)
            is SettingsEvent.VolumeButtonsPageTurnChanged -> setVolumeButtonsPageTurn(event.enabled)
        }
    }

    private fun setTheme(theme: AppTheme) {
        _uiState.update { it.copy(selectedTheme = theme) }
        viewModelScope.launch {
            userPreferencesRepository.setSelectedTheme(theme)
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOn(enabled)
        }
    }

    private fun setVolumeButtonsPageTurn(enabled: Boolean) {
        _uiState.update { it.copy(volumeButtonsPageTurn = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setVolumeButtonsPageTurn(enabled)
        }
    }
}
