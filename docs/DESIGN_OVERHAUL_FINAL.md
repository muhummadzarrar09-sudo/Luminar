# Design System Overhaul — Premium Visual Layer

## Research Applied

Based on analysis of:
- DesignDroid OLED color palette guide (7 palettes, 2026)
- ColorUXLab 60-30-10 rule for mobile
- DesignRush award-winning dark UI apps (Trust, Momento, Spectra)
- Glassmorphism implementation guides (Haze, Cloudy, RenderEffect)
- Dark mode contrast guidelines (WCAG 2.2 AA, 4.5:1 ratio)

## Color Palette Redesign

### AMOLED Dark — "Luxury Dark Gold"

The palette was tweaked for a more premium feel:

| Token | Old | New | Why |
|-------|-----|-----|-----|
| Background | `#0A0A0A` | `#050505` | Deeper black = more OLED savings |
| Surface | `#141414` | `#111111` | Subtler card lift |
| Elevated | `#1C1C1C` | `#1A1A1A` | Tighter hierarchy |
| Gold (accent) | `#E8C96A` | `#D4A843` | Less yellow, more warm gold = premium feel |
| Gold (dim) | `#9E8434` | `#8B7028` | Deeper, richer secondary |
| Text primary | `#F0F0F0` | `#EAEAEA` | Slightly off-white = softer on eyes |
| Text secondary | `#B8B8B8` | `#8A8A8A` | More contrast between primary/secondary |
| Divider | `#2A2A2A` | `#1F1F1F` | Less visible = cleaner |
| Light bg | `#FFFFFF` | `#FAFAFA` | Off-white = less harsh |

The 60-30-10 rule:
- 60% backgrounds (#050505) — the canvas
- 30% surfaces (#111111) — cards and containers
- 10% accent (#D4A843) — gold highlights, CTAs, active states

### Design Language: "Subtle Glass"

Since we can't add blur libraries (build file constraint), I simulated glassmorphism with:

1. **Subtle gold-tinted borders** (0.5dp, 10-20% opacity) on all surfaces
2. **Slight transparency** on bottom bar (95% opacity)
3. **Elevation + border combo** that creates depth without blur
4. **Gold divider glow** (8% opacity gold line) separating nav from content

This creates a "premium dark" aesthetic similar to Trust (fintech app, DesignRush winner) and Spotify's dark mode.

## Component-Level Changes

### Book Cards
- Added: 0.5dp border with `outlineVariant` at 15% opacity
- Added: 1dp default elevation, 4dp on press
- Result: Cards float slightly above background with a glass-like edge

### Continue Reading Card
- Added: 0.8dp gold border at 20% opacity
- Gold left accent stripe (kept)
- 2dp elevation
- Result: Premium card that draws the eye with subtle gold glow

### Bottom Navigation Bar
- Gold-tinted divider (8% opacity) instead of gray
- 95% opacity surface (slight content bleed-through)
- Active indicator pill (spring-animated)
- Result: Feels like frosted glass panel

### Settings Preference Groups
- Added: 0.5dp border with `outlineVariant` at 20% opacity
- Added: 1dp tonal elevation
- Result: Grouped settings look like glass panels, not flat sections

### Reader Controls
- Added: 0.5dp gold border at 10% opacity on top/bottom bars
- Increased padding (8dp horizontal, 10dp vertical)
- 75% opacity background (glassmorphism simulation)
- Theme-aware scrim behind controls
- Result: Controls feel like floating glass panels over content

## Status Bar Fix

Changed `setDecorFitsSystemWindows(false)` → `true`. The system now handles status bar padding properly. Title no longer collides with notifications/clock.

## Files Changed

- `Color.kt` — Complete palette redesign (14 color values refined)
- `Theme.kt` — Updated controls opacity for glassmorphism
- `LibraryScreen.kt` — Card borders, bottom bar glass styling, stats dialog
- `SettingsScreen.kt` — PreferenceGroup glass borders + elevation
- `ReaderScreen.kt` — Controls gold border, increased padding
- `MainActivity.kt` — Status bar fix, manifest cleanup
- `AndroidManifest.xml` — Removed crash-causing configs, simplified
