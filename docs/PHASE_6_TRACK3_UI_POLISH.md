# Phase 6 Track 3 — UI Polish

## What changed

Eight targeted improvements to make the app feel premium and responsive.

### 1. 📳 Haptic feedback
Subtle vibration on key interactions:
- **Library:** FAB tap, long-press to delete
- **Reader:** Theme toggle, font size A+/A−, search ▲/▼ match navigation

Uses `LocalHapticFeedback` with `TextHandleMove` (light tap) and `LongPress` (firm tap) feedback types — standard Android haptics, no permissions needed.

### 2. 🫧 Card press animation
Book cards in the library grid now have a **scale-down bounce** on press:
- Scales to 96% on finger down
- Springs back with `DampingRatioMediumBouncy` on release
- Uses `detectTapGestures` for press/release/tap/long-press in a single gesture handler (replaces `combinedClickable` for the grid)

### 3. ✨ Animated item appearance
Books in the grid animate in with a **fade** when the list loads or items change:
- Uses `Modifier.animateItem(fadeInSpec = tween(300))`
- Items smoothly appear instead of popping in

### 4. 🔽 FAB scroll-hide
The floating import button **hides when scrolling down** and reappears when at the top or stopped:
- Slides out downward + fades out
- Springs back in when scrolling stops or near top
- Uses `derivedStateOf` on `LazyGridState` for zero-cost observation

### 5. 🎭 Smoother navigation transitions
All screen transitions now use **cubic easing** instead of `FastOutSlowInEasing`:
- `EaseOutCubic (0.33, 1, 0.68, 1)` — for entering screens (decelerates smoothly)
- `EaseInCubic (0.32, 0, 0.67, 0)` — for exiting screens (accelerates smoothly)
- Duration bumped from 300ms → 350ms for a more premium feel
- Library→Reader: horizontal slide
- Library→Settings: vertical slide up/down
- All transitions feel noticeably smoother

### 6. 🌅 Empty state entrance animation
The "Add your first book" empty library state now **fades in and slides up** on first appearance:
- Alpha animates 0→1 over 600ms
- Translates up 24dp with a low-bouncy spring
- Icon, text, and button all appear together

### 7. 💀 Loading skeleton (from Track 2)
Already in place — the shimmer skeleton grid from Track 2 provides a smooth loading→content transition.

### 8. 📝 Loading messages (from Track 2)
Already in place — "Loading comic…" / "Parsing document…" messages during reader content loading.

## Files changed (NO build files touched)

- `LibraryScreen.kt` — Haptics, card scale animation, animated item entrance, FAB scroll-hide with AnimatedVisibility, empty state fade-in, new imports (haptics, animation, gesture)
- `NavGraph.kt` — Full rewrite: `CubicBezierEasing` curves replace `FastOutSlowInEasing`, duration 300→350ms
- `ReaderScreen.kt` — Haptic feedback on theme toggle, font size, search navigation
