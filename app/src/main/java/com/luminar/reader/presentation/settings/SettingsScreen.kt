// app/src/main/java/com/luminar/reader/presentation/settings/SettingsScreen.kt
package com.luminar.reader.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luminar.reader.R
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.LuminarTitleFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = LuminarTitleFont,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                SectionHeader(text = "READING")
            }

            item {
                PreferenceGroup {
                    ThemeOptionRow(
                        title = "AMOLED Dark",
                        subtitle = "True black reading interface",
                        selected = uiState.selectedTheme == AppTheme.DARK_AMOLED,
                        onClick = {
                            viewModel.onEvent(
                                SettingsEvent.ThemeSelected(AppTheme.DARK_AMOLED)
                            )
                        }
                    )

                    PreferenceDivider()

                    ThemeOptionRow(
                        title = "Sepia",
                        subtitle = "Warm paper tone",
                        selected = uiState.selectedTheme == AppTheme.SEPIA,
                        onClick = {
                            viewModel.onEvent(
                                SettingsEvent.ThemeSelected(AppTheme.SEPIA)
                            )
                        }
                    )

                    PreferenceDivider()

                    ThemeOptionRow(
                        title = "Light",
                        subtitle = "Classic white background",
                        selected = uiState.selectedTheme == AppTheme.LIGHT,
                        onClick = {
                            viewModel.onEvent(
                                SettingsEvent.ThemeSelected(AppTheme.LIGHT)
                            )
                        }
                    )

                    PreferenceDivider()

                    SwitchPreferenceRow(
                        title = "Keep screen on",
                        subtitle = "Prevent the display from sleeping while reading",
                        checked = uiState.keepScreenOn,
                        onCheckedChange = {
                            viewModel.onEvent(
                                SettingsEvent.KeepScreenOnChanged(it)
                            )
                        }
                    )

                    PreferenceDivider()

                    SwitchPreferenceRow(
                        title = "Volume buttons turn pages",
                        subtitle = "Use volume up/down while the reader is open",
                        checked = uiState.volumeButtonsPageTurn,
                        onCheckedChange = {
                            viewModel.onEvent(
                                SettingsEvent.VolumeButtonsPageTurnChanged(it)
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader(text = "ABOUT")
            }

            item {
                PreferenceGroup {
                    StaticPreferenceRow(
                        title = "App version",
                        subtitle = uiState.appVersion
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Built with Luminar Reader",
                        subtitle = "Local-first personal reading"
                    )
                }
            }

            item {
                SectionHeader(text = "COMING SOON — AI FEATURES")
            }

            item {
                PreferenceGroup {
                    DisabledAiSettings(
                        ollamaBaseUrl = uiState.ollamaBaseUrl,
                        ollamaModel = uiState.ollamaModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String
) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 8.dp
        ),
        color = LuminarGold,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun PreferenceGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun SwitchPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable {
            onCheckedChange(!checked)
        },
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun StaticPreferenceRow(
    title: String,
    subtitle: String
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun DisabledAiSettings(
    ollamaBaseUrl: String,
    ollamaModel: String
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        OutlinedTextField(
            value = ollamaBaseUrl,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Ollama server URL") },
            enabled = false,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = ollamaModel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Model name") },
            enabled = false,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {},
            enabled = false
        ) {
            Text(text = "Connect")
        }
    }
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}
