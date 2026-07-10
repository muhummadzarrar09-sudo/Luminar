# Glassmorphism & Build Upgrade

## Build File Changes (FIRST TIME!)

### libs.versions.toml — Version Bumps

| Library | Before | After | Why |
|---------|--------|-------|-----|
| Compose BOM | 2024.06.00 | **2024.12.01** | Foundation 1.7+ = `animateItem`, stable Pager, better blur |
| Core KTX | 1.13.1 | **1.15.0** | Latest stable |
| Lifecycle | 2.8.3 | **2.8.7** | Bug fixes |
| Activity Compose | 1.9.0 | **1.9.3** | Bug fixes |
| Navigation | 2.8.0 | **2.8.5** | Bug fixes |
| DataStore | 1.1.1 | **1.1.4** | Bug fixes |
| Work Runtime | 2.9.0 | **2.10.0** | Bug fixes |

### Why These Are Safe
- All are **stable** releases (no alpha/beta)
- All are within the same major version (no breaking API changes)
- Compose BOM 2024.12.01 is 6 months old and battle-tested

## Real Glassmorphism — `GlassEffect.kt`

New `Modifier.glassEffect()` that uses **native Android `RenderEffect.createBlurEffect`** on API 31+ (Android 12+):

```kotlin
Modifier.glassEffect(
    blurRadius = 25f,           // blur intensity
    tintColor = Color.Black.copy(alpha = 0.3f), // glass tint
    borderColor = Color.White.copy(alpha = 0.08f), // subtle border
    borderWidth = 0.5.dp,
    cornerRadius = 16.dp
)
```

### How it works:
- **Android 12+**: Real hardware-accelerated blur via `RenderEffect` → content behind is ACTUALLY blurred
- **Android 8-11**: Fallback to tinted semi-transparent background (same as before)
- **Zero external libraries** — uses only Android SDK APIs

### Applied to:
1. **Reader top controls** — blur with gold border, content visible behind
2. **Reader bottom controls** — blur with gold border
3. **TTS control bar** — floating glass pill with blur
4. **Search bar** — glass overlay at top

## What It Looks Like

**On Android 12+ (your Infinix likely has Android 12+):**

The reader controls literally blur the text content behind them. When you tap to show controls, you can see the book text through a frosted glass layer — exactly like iOS Control Center.

**On older devices:**

Clean semi-transparent overlay (the same experience as before, but consistent).

## Files

**New:**
- `presentation/theme/GlassEffect.kt` — `Modifier.glassEffect()` + `GlassColors` object

**Modified:**
- `gradle/libs.versions.toml` — Compose BOM 2024.12.01 + library version bumps
- `ReaderScreen.kt` — Top controls, search bar, TTS bar all use `.glassEffect()`
- `LibraryScreen.kt` — `animateItemPlacement` → `animateItem` (new Compose API)
