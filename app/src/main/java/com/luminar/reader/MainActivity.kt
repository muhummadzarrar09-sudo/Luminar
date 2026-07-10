// app/src/main/java/com/luminar/reader/MainActivity.kt
package com.luminar.reader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luminar.reader.data.local.datastore.UserPreferences
import com.luminar.reader.data.local.datastore.UserPreferencesRepository
import com.luminar.reader.navigation.LuminarNavGraph
import com.luminar.reader.presentation.reader.ReaderInputController
import com.luminar.reader.presentation.theme.LuminarReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var readerInputController: ReaderInputController

    private var volumeButtonsPageTurn: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesRepository.userPreferences.collect { preferences ->
                    volumeButtonsPageTurn = preferences.volumeButtonsPageTurn
                }
            }
        }

        setContent {
            val preferences = userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            ).value

            LuminarReaderTheme(selectedTheme = preferences.selectedTheme) {
                LuminarNavGraph(
                    hasSeenOnboarding = true,
                    onOnboardingComplete = {
                        lifecycleScope.launch {
                            userPreferencesRepository.setOnboardingComplete()
                        }
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeTurnKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (
            isVolumeTurnKey &&
            volumeButtonsPageTurn &&
            readerInputController.isReaderOpen
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> readerInputController.previousPage()
                    KeyEvent.KEYCODE_VOLUME_DOWN -> readerInputController.nextPage()
                }
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}
