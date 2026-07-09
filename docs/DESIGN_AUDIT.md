# Luminar Reader — Design System Audit

*Conducted July 2026 · Standards: Material Design 3, WCAG 2.2 AA, Thumb Zone UX*

---

## Executive Summary

Luminar's feature set is competitive with $15 apps at $4.99. But the visual design has **accumulated technical debt** across 20+ build phases. It works, but it doesn't look like a $100K agency built it. This audit identifies every design violation, inconsistency, and missed opportunity — then provides a systematic fix plan.

**Starting grade: B-** (functional but inconsistent)
**Current grade: A-** (systematic tokens, consistent icons, proper hierarchy)
**Target grade: A** (post-launch refinements)

---

## 🔴 CRITICAL ISSUES

### 1. Icon System Chaos
**Problem:** The app uses a MIX of emoji icons (📑🔊🔖🏷️📊☰⊞⏹⏮▶⏭⏸☑☐) and Material vector drawables (`ic_search_24`, `ic_settings_24`, etc.). This looks unprofessional — emoji render differently per device/OS version and feel amateur.

**Fix:** Replace ALL emoji with proper Material Icons vectors. The icon language should be 100% consistent.

| Current (emoji) | Replace with |
|-----------------|-------------|
| 📑 TOC | `ic_toc_24.xml` (list icon) |
| 🔊 TTS | `ic_volume_up_24.xml` |
| 🔖/🏷️ Bookmark | `ic_bookmark_24.xml` / `ic_bookmark_border_24.xml` |
| 📊 Stats | `ic_bar_chart_24.xml` |
| ☰ List view | `ic_view_list_24.xml` |
| ⊞ Grid view | `ic_grid_view_24.xml` |
| ⏹⏮▶⏭⏸ TTS controls | `ic_stop_24`, `ic_skip_previous_24`, `ic_play_arrow_24`, `ic_skip_next_24`, `ic_pause_24` |
| ☑/☐ Task list | Render with `Canvas` drawn checkboxes |
| 🎙 Voice profile | `ic_mic_24.xml` |
| → Arrow | `ic_chevron_right_24.xml` |

### 2. Spacing Anarchy
**Problem:** 6 different horizontal paddings (6, 8, 12, 14, 16, 20dp) and 5 different vertical paddings (2, 4, 6, 8, 10dp) used inconsistently. No spacing system.

**Fix:** Adopt the **Material 8dp grid**:

| Token | Value | Usage |
|-------|-------|-------|
| `SpaceXS` | 4dp | Inline tight spacing |
| `SpaceSM` | 8dp | Between related elements |
| `SpaceMD` | 12dp | Component internal padding |
| `SpaceLG` | 16dp | Section spacing, screen horizontal padding |
| `SpaceXL` | 24dp | Between sections |
| `SpaceXXL` | 32dp | Page-level margins |

### 3. Corner Radius Salad
**Problem:** 7 different corner radii used (2, 4, 6, 8, 10, 12, 16dp). No visual consistency.

**Fix:** Material 3 Shape tokens:

| Token | Value | Usage |
|-------|-------|-------|
| `RadiusSM` | 8dp | Chips, badges, small cards |
| `RadiusMD` | 12dp | Cards, dialogs |
| `RadiusLG` | 16dp | Bottom sheets, large surfaces |
| `RadiusFull` | 50% | Circular buttons, avatars |

### 4. Font Size Scatter
**Problem:** 15+ hardcoded font sizes (9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 26, 30sp). Should use `MaterialTheme.typography.*` roles instead.

**Fix:** Map to M3 typography roles:

| Current usage | M3 role | Size |
|--------------|---------|------|
| App title "Luminar" 26sp | `headlineMedium` | 28sp |
| Section titles | `titleMedium` | 16sp |
| Body text | `bodyLarge` | 16sp |
| Secondary text | `bodyMedium` | 14sp |
| Captions/badges | `labelSmall` | 11sp |
| Filter chips | `labelMedium` | 12sp |

---

## 🟡 MODERATE ISSUES

### 5. Hardcoded Colors
**Problem:** 6 instances of `Color(0x...)` in LibraryScreen outside the theme system. These don't respond to theme changes.

**What to fix:**
- `Color(0xFF171100)` → `MaterialTheme.colorScheme.onPrimary` (FAB content)
- `Color(0xFF3B3014)` → define as theme token
- `Color(0xFF111111)` → define as theme token
- `Color(0xD9000000)` → `MaterialTheme.colorScheme.scrim`

### 6. Bottom Bar Visual Weight
**Problem:** The bottom nav has 5 items but they all look the same weight. The center "Import" button has a gold circle but it doesn't pop enough. No visual hierarchy.

**Fix:**
- Center import: larger (48dp circle), elevated with shadow
- Other items: more muted, smaller icons
- Active state: filled icon + gold tint
- Inactive state: outlined icon + gray tint
- Add subtle top border/divider above the bar

### 7. Continue Reading Card
**Problem:** The card exists but feels flat — same surface color as book cards, no visual distinction.

**Fix:**
- Add a subtle gold gradient border or left accent stripe
- Slightly larger cover thumbnail
- Add a pulsing "▶" indicator or a gradient shimmer to draw attention
- Distinct surface color (slightly elevated)

### 8. Empty States
**Problem:** The empty library state has text + button, but no personality. Feels generic.

**Fix:**
- Animate the book icon (gentle float/pulse)
- Add a subtitle with personality: "Your library is empty — but not for long"
- Make the import button gold-filled instead of outlined
- Add subtle background pattern or illustration

### 9. TTS Control Bar
**Problem:** The TTS bar uses emoji for controls which renders differently per device. Also floats at a fixed bottom position that may overlap with the reading progress bar.

**Fix:**
- Replace all emoji with Material Icons
- Add a subtle slide-up animation when appearing
- Ensure it sits ABOVE the progress bar (proper z-ordering)
- Add a semi-transparent scrim behind it

### 10. Reader Controls Overlap
**Problem:** When controls are visible, there's no scrim/backdrop behind them. The text content behind is fully visible and distracting.

**Fix:**
- Add a semi-transparent scrim (50% black for AMOLED, 30% black for Light) behind the controls overlay
- This is what Kindle, Moon+, and every professional reader does

---

## 🟢 QUICK WINS (Low effort, high polish)

### 11. Loading Transitions
- Add `crossfade` between loading skeleton → real content
- Fade the "Continue Reading" card in on first appearance

### 12. Card Hover/Press States
- Already have scale animation ✅
- Add: ripple effect on cards (Material default)

### 13. Dividers
- Use `HorizontalDivider` with `MaterialTheme.colorScheme.outlineVariant` consistently
- Current: some dividers use hardcoded alpha values

### 14. Status Bar Color
- Library: status bar should match the background color
- Reader: status bar should be transparent (immersive mode handles this) ✅

### 15. Typography Hierarchy
- "Luminar" title: increase weight to Bold, not SemiBold
- Section headers in settings: use `labelLarge` consistently ✅
- Stats values: use `headlineSmall` for the numbers, `bodySmall` for labels

---

## 📐 PROPOSED DESIGN TOKENS

```kotlin
// Spacing
object LuminarSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// Corner Radius
object LuminarRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val full = 50 // percentage
}

// Elevation
object LuminarElevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
}

// Icon Size
object LuminarIconSize {
    val sm = 20.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

// Touch Targets
object LuminarTouch {
    val min = 48.dp  // Material 3 minimum
}
```

---

## 🎯 EXECUTION PRIORITY

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| ✅ P0 | Replace emoji icons with vectors | Huge | Medium | **DONE — 14 vectors, 0 emoji** |
| ✅ P0 | Design tokens (spacing, radius) | Huge | Low | **DONE — Tokens.kt** |
| ✅ P1 | Reader controls scrim/backdrop | High | Low | **DONE — 40% black overlay** |
| ✅ P1 | Bottom bar visual hierarchy | High | Medium | **DONE — divider, 48dp import, shadow** |
| ✅ P1 | Continue Reading card polish | Medium | Low | **DONE — gold stripe, elevation** |
| ✅ P1 | Typography from M3 roles | Medium | Medium | **DONE — 14 refs converted** |
| 🟢 P2 | Empty state animation | Low | Low | Already done (Phase 6 Track 3) |
| 🟢 P2 | Loading transitions | Low | Low | Already done (shimmer skeleton) |
| 🟢 P2 | Hardcoded color cleanup | Low | Low | Deferred to post-launch |

---

## The Bottom Line

The app has **premium features wrapped in a prototype UI**. The functionality beats apps 3× its price, but the visual layer says "weekend project" instead of "$100K agency."

The fix isn't a rewrite — it's a **systematic token layer** (spacing, radius, elevation, icons) applied consistently across all screens. ~400 lines of token definitions + ~200 targeted edits across screens = professional-grade output.
