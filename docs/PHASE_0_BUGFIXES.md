# Luminar Reader — Bug Fixes (Round 2)

## Files changed (NO build files touched)

### Bug A: Reader controls clipped (top bar + bottom slider cut off)

**Root cause:** The previous fix changed `setDecorFitsSystemWindows(window, true)`.
With `true`, the system itself reserves space for bars, but when the reader hides
system bars for immersive mode, Compose's `statusBarsPadding()` / `navigationBarsPadding()`
return 0 because the system already consumed those insets. This left the reader
controls flush against the screen edges — clipped behind where bars would appear.

**Fix applied:**

1. **`MainActivity.kt`** — Reverted to `setDecorFitsSystemWindows(window, false)`
   (edge-to-edge). Compose handles all insets via its own padding modifiers.
   
2. **`styles.xml`** — Added `android:statusBarColor` and `android:navigationBarColor`
   as `@android:color/transparent` so the native theme supports edge-to-edge
   (transparent system bars that Compose can draw behind).

3. **`ReaderScreen.kt`** — Improved the `DisposableEffect` for system bar hiding:
   - Set `systemBarsBehavior` BEFORE calling `hide()` (order matters)
   - Null-safe `decorView` extraction
   - `onDispose` now resets `systemBarsBehavior = BEHAVIOR_DEFAULT` AND shows bars

### Bug B: Gesture nav bar not appearing on swipe

**Root cause:** Same as Bug A — `decorFitsSystemWindows=true` broke the insets
contract. With edge-to-edge restored, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
works correctly: swipe from the screen edge where the bar lives (bottom edge for
nav bar, top edge for status bar) to reveal transient bars.

**Fix:** Resolved by the same edge-to-edge restoration above.

### Bug C: Icons makeover (both launcher and in-app)

**Launcher icon** (`ic_launcher_foreground.xml`):
- Redesigned open-book vector with curved pages, page-curl detail, and text lines
- Better depth with light/dark gold tones (`#E8C96A` / `#F0D87A` / `#C4A94E`)
- Properly centered in the 108dp adaptive-icon safe zone

**In-app icons** (all 24dp vectors):
- `ic_arrow_back_24.xml` — Replaced with Material Symbols chevron-left (cleaner)
- `ic_settings_24.xml` — Replaced with Material Symbols settings gear (rounded)
- `ic_palette_24.xml` — Replaced with Material Symbols palette (rounded)
- `ic_add_24.xml` — Replaced with Material Symbols add/plus (rounded)
- `ic_auto_stories_48.xml` — Replaced with Material Symbols auto_stories (rounded)

All use 960×960 viewport (Material Symbols standard) for crisper rendering.

---

## Files NOT changed

- `build.gradle.kts` (app or project level)
- `settings.gradle.kts`
- `gradle-wrapper.properties`
- `libs.versions.toml`
- Any other Gradle/build file
- `Theme.kt`, `Color.kt`, `Type.kt` — already correct
- `SettingsScreen.kt` — already uses `MaterialTheme.colorScheme`
