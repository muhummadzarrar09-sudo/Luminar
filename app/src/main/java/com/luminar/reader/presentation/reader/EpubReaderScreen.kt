package com.luminar.reader.presentation.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luminar.reader.R
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.next
import com.luminar.reader.presentation.theme.readerBackgroundColor
import com.luminar.reader.presentation.theme.readerControlsContainerColor
import com.luminar.reader.presentation.theme.readerControlsContentColor

@Composable
fun EpubReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }

        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    DisposableEffect(activity, uiState.keepScreenOn) {
        val window = activity?.window
        if (uiState.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        viewModel.setReaderVisible(true)
        onDispose { viewModel.setReaderVisible(false) }
    }

    val backgroundColor = uiState.currentTheme.readerBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = LuminarGold
                )
            }

            uiState.book == null || uiState.publication == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unable to open EPUB",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = currentOnNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = LuminarGold)) {
                        Text("Back")
                    }
                }
            }

            else -> {
                EpubReaderViewContainer(
                    activity = activity as? FragmentActivity,
                    uiState = uiState,
                    onToggleControls = { viewModel.onEvent(ReaderEvent.ToggleControls) }
                )

                EpubControlsOverlay(
                    uiState = uiState,
                    onNavigateBack = currentOnNavigateBack,
                    onToggleTheme = { viewModel.onEvent(ReaderEvent.ThemeChanged(uiState.currentTheme.next())) },
                    onInteraction = viewModel::onControlsInteraction
                )
            }
        }
    }
}

@Composable
private fun EpubReaderViewContainer(
    activity: FragmentActivity?,
    uiState: EpubReaderUiState,
    onToggleControls: () -> Unit
) {
    val backgroundColor = uiState.currentTheme.readerBackgroundColor()

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Text(
            text = "Reading: ${uiState.book?.title}",
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun EpubControlsOverlay(
    uiState: EpubReaderUiState,
    onNavigateBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onInteraction: () -> Unit
) {
    AnimatedVisibility(
        visible = uiState.showControls,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300), initialOffsetY = { -it / 4 }),
        exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300), targetOffsetY = { -it / 4 })
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val containerColor = uiState.currentTheme.readerControlsContainerColor()
            val contentColor = uiState.currentTheme.readerControlsContentColor()

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = containerColor,
                contentColor = contentColor
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onInteraction(); onNavigateBack() }) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back_24), contentDescription = "Back", tint = contentColor)
                    }
                    Text(
                        text = uiState.book?.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = contentColor,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onInteraction(); onToggleTheme() }, colors = ButtonDefaults.textButtonColors(contentColor = LuminarGold)) {
                        Icon(painter = painterResource(R.drawable.ic_palette_24), contentDescription = null, tint = LuminarGold)
                        Text(modifier = Modifier.padding(start = 6.dp), text = when (uiState.currentTheme) {
                            AppTheme.DARK_AMOLED -> "AMOLED"
                            AppTheme.SEPIA -> "Sepia"
                            AppTheme.LIGHT -> "Light"
                        })
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
