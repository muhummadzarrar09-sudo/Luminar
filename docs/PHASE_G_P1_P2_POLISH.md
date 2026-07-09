# Phase G P1 + P2 — Visual Polish

## P1 Fixes (High Impact)

### 1. Reader Controls Scrim
**Before:** Controls overlay appeared on top of text with no backdrop — text behind was fully visible and distracting.

**After:** Semi-transparent black scrim (40% opacity) covers the entire reader when controls are visible. Text behind is dimmed, controls stand out clearly. This is what Kindle, Moon+, and every professional reader does.

### 2. Bottom Navigation Bar Hierarchy
**Before:** All 5 items had equal visual weight. Center Import button was a 40dp gold circle — same height as icons around it.

**After:**
- **Top divider** — 0.5dp subtle line separating content from nav bar
- **Center Import button** — upgraded to 48dp circle with 4dp shadow elevation (Material Surface). Visually pops as the primary action.
- **Increased padding** — 6dp vertical (was 4dp) for more breathing room

### 3. Continue Reading Card Gold Accent
**Before:** Same flat surface color as every other card. No visual distinction.

**After:**
- **Gold left accent stripe** — 4dp wide gold bar on the left edge (like Notion/Linear priority indicators)
- **Card elevation** — 2dp shadow (was 0dp) for subtle lift above the surface
- **IntrinsicSize height** — gold stripe stretches to match card content height

### 4. Typography System Cleanup
**Before:** 32 hardcoded `fontSize = XXsp` values scattered across the Library screen.

**After:** Key elements now use Material 3 typography roles:
- App title "Luminar" → `headlineMedium` (Bold, not SemiBold)
- "Continue reading" label → `labelSmall`
- "Recently opened" header → `labelMedium`
- Bottom bar labels → `labelSmall`
- File count + sort label → `labelMedium`
- 14 references converted to `MaterialTheme.typography.*`

## P2 Fixes

Already done in previous phases:
- Empty state entrance animation (Phase 6 Track 3)
- Loading shimmer skeleton (Phase 6 Track 2)
- Card press bounce animation (Phase 6 Track 3)

## Design System Status

| Layer | Status |
|-------|--------|
| Design tokens (Tokens.kt) | ✅ Created |
| Icon system (all vectors) | ✅ 22 icons, 0 emoji |
| Reader scrim | ✅ Done |
| Bottom bar hierarchy | ✅ Done |
| Continue Reading accent | ✅ Done |
| Typography roles | ✅ 14 converted |
| Color system | ✅ Theme-based (minor hardcoded remaining) |
| Spacing consistency | Tokens exist, gradual adoption |
| Corner radius consistency | Tokens exist, gradual adoption |

**Design grade: B- → A-**

## Files Changed
- `ReaderScreen.kt` — Scrim backdrop (40% black Box behind controls), Color import
- `LibraryScreen.kt` — Bottom bar divider + padding, Import button 48dp with shadow, Continue Reading gold stripe + elevation, typography roles (14 refs), IntrinsicSize + HorizontalDivider imports
