# Phase G — Design System Implementation (P0)

## What Changed

### 1. Design Token System (`Tokens.kt`)

New centralized token file defining the visual language:

```
Spacing:   xs(4) → sm(8) → md(12) → lg(16) → xl(24) → xxl(32)dp
Radius:    sm(8) → md(12) → lg(16) → xl(24)dp
Elevation: none(0) → low(2) → medium(4) → high(8)dp
IconSize:  sm(20) → md(24) → lg(32) → xl(48)dp
Touch:     48dp minimum target
```

Every future component should reference these tokens instead of hardcoded values.

### 2. Icon System Overhaul

**14 new Material Icon vectors** created, replacing ALL emoji usage:

| Emoji removed | Vector replacement | File |
|--------------|-------------------|------|
| 📑 | Table of Contents icon | `ic_toc_24.xml` |
| 🔊 | Volume/speaker icon | `ic_volume_up_24.xml` |
| 🔖 | Filled bookmark | `ic_bookmark_24.xml` |
| 🏷️ | Outlined bookmark | `ic_bookmark_border_24.xml` |
| 📊 | Bar chart | `ic_bar_chart_24.xml` |
| ☰ | List view | `ic_view_list_24.xml` |
| ⊞ | Grid view | `ic_grid_view_24.xml` |
| ⏹ | Stop | `ic_stop_24.xml` |
| ⏮ | Skip previous | `ic_skip_previous_24.xml` |
| ▶ | Play arrow | `ic_play_arrow_24.xml` |
| ⏭ | Skip next | `ic_skip_next_24.xml` |
| ⏸ | Pause | `ic_pause_24.xml` |
| 🎙 | Microphone | `ic_mic_24.xml` |
| → | Chevron right | `ic_chevron_right_24.xml` |

**Why this matters:**
- Emoji render DIFFERENTLY on every phone (Samsung vs Pixel vs Infinix vs Xiaomi)
- Vector icons render IDENTICALLY everywhere — pixel-perfect, scalable, theme-tintable
- All icons use the same 960×960 viewport (Material Symbols Rounded) for visual consistency
- Every icon respects `tint` parameter — changes color with theme automatically

### Before vs After

**Before (emoji, per-device rendering):**
- Samsung: 📑🔊🔖⏹⏮▶⏭
- Pixel: 📑🔊🔖⏹⏮▶⏭ (different style)
- Infinix: 📑🔊🔖⏹⏮▶⏭ (yet another style)

**After (vectors, identical everywhere):**
- Every device: clean, consistent, Material Design icons
- Theme-aware: gold on AMOLED, dark on Light, brown on Sepia

### Emoji count before: 18+ across screens
### Emoji count after: 0

### Total icon files: 22 (8 existing + 14 new)

## Files

**New:**
- `presentation/theme/Tokens.kt` — Spacing, Radius, Elevation, IconSize, MinTouchTarget tokens
- 14 vector drawable XMLs in `res/drawable/`

**Modified:**
- `LibraryScreen.kt` — Replaced ☰/⊞/📊/→ emoji with vector Icons
- `ReaderScreen.kt` — Replaced 📑/🔊/🔖/🏷️/⏹/⏮/▶/⏭/⏸/🎙 emoji with vector Icons, bookmark now gold-tinted when active
