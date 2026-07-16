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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luminar.reader.R
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.FontScale
import com.luminar.reader.data.model.ScrollMode
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
                SectionHeader(text = "TEXT READER")
            }

            item {
                PreferenceGroup {
                    // Font scale options
                    FontScale.entries.forEach { scale ->
                        ThemeOptionRow(
                            title = scale.displayName,
                            subtitle = when (scale) {
                                FontScale.TINY -> "75% — compact reading"
                                FontScale.SMALL -> "88% — slightly smaller"
                                FontScale.NORMAL -> "100% — default size"
                                FontScale.LARGE -> "115% — easier on the eyes"
                                FontScale.HUGE -> "135% — large text"
                                FontScale.MASSIVE -> "160% — accessibility"
                            },
                            selected = uiState.fontScale == scale,
                            onClick = {
                                viewModel.onEvent(SettingsEvent.FontScaleSelected(scale))
                            }
                        )
                        if (scale != FontScale.entries.last()) {
                            PreferenceDivider()
                        }
                    }
                }
            }

            item {
                PreferenceGroup {
                    ThemeOptionRow(
                        title = "Vertical scroll",
                        subtitle = "Continuous scrolling for text files",
                        selected = uiState.defaultScrollMode == ScrollMode.VERTICAL_SCROLL,
                        onClick = {
                            viewModel.onEvent(
                                SettingsEvent.ScrollModeSelected(ScrollMode.VERTICAL_SCROLL)
                            )
                        }
                    )

                    PreferenceDivider()

                    ThemeOptionRow(
                        title = "Paged",
                        subtitle = "Page-by-page like PDF reading",
                        selected = uiState.defaultScrollMode == ScrollMode.PAGED,
                        onClick = {
                            viewModel.onEvent(
                                SettingsEvent.ScrollModeSelected(ScrollMode.PAGED)
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader(text = "READING STATS")
            }

            item {
                PreferenceGroup {
                    StaticPreferenceRow(
                        title = "Total reading time",
                        subtitle = formatReadingTime(uiState.totalReadingTimeMinutes)
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Books opened",
                        subtitle = "${uiState.totalBooksOpened} books"
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Current streak",
                        subtitle = if (uiState.currentStreak > 0)
                            "${uiState.currentStreak} day${if (uiState.currentStreak != 1) "s" else ""} 🔥"
                        else "Start reading today!"
                    )
                }
            }

            item {
                SectionHeader(text = "PREMIUM DESIGN THEME PRESETS (v2.0 PREVIEW)")
            }

            item {
                PreferenceGroup {
                    designPresetsList.forEachIndexed { idx, preset ->
                        PresetOptionRow(
                            title = preset.title,
                            subtitle = preset.subtitle,
                            premium = true
                        )
                        if (idx < designPresetsList.lastIndex) {
                            PreferenceDivider()
                        }
                    }
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
                        title = "Supported formats",
                        subtitle = "PDF, EPUB, DOCX, XLSX, PPTX, MOBI, AZW3, CBZ, FB2, RTF, CHM, XPS, ODT, Markdown, Code, and 15+ more"
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Built with Luminar Reader",
                        subtitle = "Local-first · Privacy-focused · No ads ever"
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Security",
                        subtitle = "All files stored locally · No data collection · No analytics · HTTPS enforced"
                    )

                    PreferenceDivider()

                    StaticPreferenceRow(
                        title = "Architecture",
                        subtitle = "Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · Zero external parser dependencies"
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
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 1.dp
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

private fun formatReadingTime(minutes: Long): String {
    return when {
        minutes < 1 -> "Just getting started"
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    }
}

@Composable
private fun PresetOptionRow(
    title: String,
    subtitle: String,
    premium: Boolean
) {
    ListItem(
        headlineContent = { Text(text = title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            if (premium) {
                Surface(
                    color = LuminarGold.copy(alpha = 0.15f),
                    contentColor = LuminarGold,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PREMIUM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

private data class DesignPreset(val title: String, val subtitle: String)

private val designPresetsList = listOf(
    DesignPreset("Skeuomorphism", "Early iOS tactile 3D leather calendars & wooden shelves"),
    DesignPreset("Flat design", "Solid colors and simple shapes with zero gradients or shadows"),
    DesignPreset("Material Design", "Flat layers with subtle depth shadows simulating paper sheets"),
    DesignPreset("Neumorphism (soft UI)", "Extruded monochrome surfaces with soft volumetric shadows"),
    DesignPreset("Glassmorphism", "Frosted glass overlays with blur, transparency, and borders"),
    DesignPreset("Claymorphism", "Puffy, rounded clay-like shapes with soft volumetric shadows"),
    DesignPreset("Minimalism", "Stripped down to essentials, heavy white space, clean typography"),
    DesignPreset("Brutalism / Neubrutalism", "Raw typography, thick black borders, and clashing bold colors"),
    DesignPreset("Maximalism", "Dense layouts, bold colors, layered textures, and high visual density"),
    DesignPreset("Swiss / International Typographic Style", "Grid-based layouts, sans-serif typography, and strong hierarchy"),
    DesignPreset("Retro / Vaporwave / Y2K", "Nostalgic pixel art, chrome text, glitch effects, and neon gradients"),
    DesignPreset("Cyberpunk / Neon UI", "OLED black backgrounds with glowing neon-line accents"),
    DesignPreset("Bento grid design", "Modular, rounded grid boxes organized like Apple product slides"),
    DesignPreset("Dark mode / Monochrome", "Near-black backgrounds with limited accents to reduce eye strain"),
    DesignPreset("Aurora / Mesh gradient design", "Soft, blurred, colorful gradient blobs as fluid backgrounds"),
    DesignPreset("Paper UI / Papercraft", "Layered cutout shapes simulating stacked sheets of real paper"),
    DesignPreset("Editorial / Print-inspired web", "Magazine layouts: serif headlines, column grids, and pull quotes"),
    DesignPreset("Riso (Risograph) style", "Grainy, slightly misaligned color layers resembling screen-printing"),
    DesignPreset("Newsprint UI", "Off-white cream backgrounds, halftone dot textures, and news serif"),
    DesignPreset("Parchment / Vellum", "Warm cream tones, subtle paper-grain textures, and soft aged edges"),
    DesignPreset("Slate / Charcoal minimalism", "Calmer dark grey-blue backgrounds with muted accent colors"),
    DesignPreset("Concrete / Stone UI", "Subtle noise texture on cards resembling raw concrete and slate"),
    DesignPreset("Linen / Fabric texture", "Soft woven woven-fabric textures used in classic macOS notes"),
    DesignPreset("Chalkboard / Blackboard UI", "Dark slate-green background with chalk-style sketchy typography"),
    DesignPreset("Blueprint UI", "Navy background with white grid lines and technical-drawing icons"),
    DesignPreset("Notebook / Sketch UI", "Lined paper backgrounds, handwritten-style fonts, and doodle icons"),
    DesignPreset("Terrazzo", "Speckled multi-color stone pattern as accent blocks"),
    DesignPreset("Cork board UI", "Textured tan cork background with pinned cards and post-it notes"),
    DesignPreset("Grain/Noise minimalism", "Clean flat layout with a subtle film-grain texture overlay"),
    DesignPreset("Frosted stone / Matte slate cards", "Textured matte slate-grey finish with soft inset shadows")
)
