# SSS+ Design PRD — Luminar Reader

*Research: 10 sources, July 2026 · Standards: M3 Expressive, WCAG 2.2 AA, 2026 Dribbble/Behance/DesignRush winners*

---

## What separates A- from SSS+

Based on analysis of award-winning apps (Headspace, Airbnb, Linear, Things 3, Arc Browser, Spotify) and 2026 design trends:

| A- (where we are) | SSS+ (where we need to be) |
|---|---|
| Consistent icons ✅ | **Spring-based motion** on every interaction |
| Design tokens exist | **Every value references a token** (zero magic numbers) |
| Controls have scrim | **Glassmorphism blur** on controls overlay |
| Cards have shadow | **Micro-interactions** on every card (ripple, press, reveal) |
| Bottom nav works | **Active indicator pill** (M3 Expressive style) |
| Functional loading | **Content-aware skeleton** that matches real layout |
| Title uses serif | **Consistent typographic hierarchy** across ALL screens |

---

## THE 7 UPGRADES TO SSS+

### 1. 🎯 Bottom Nav Active Indicator (M3 Expressive)
**What:** The active bottom nav item gets a **pill-shaped gold background** behind the icon — exactly like Material 3 NavigationBar active indicator.

**Why:** Current bottom bar has no visual distinction between active/inactive. Users can't tell which tab they're on. M3 Expressive found users identify active items **4× faster** with indicator pills.

### 2. 🌊 Reader Controls Glassmorphism
**What:** Replace the solid-color reader controls with **frosted glass** — semi-transparent background with backdrop blur effect.

**Why:** Solid opaque controls feel heavy and dated. Glassmorphism (the #1 design trend 2025-2026) creates depth while keeping context visible. Headspace, Arc Browser, and iOS all use this.

### 3. ✨ Spring-Based Motion System
**What:** Replace ALL `tween` animations with **spring** physics. Cards, controls, navigation — everything bounces naturally.

**Why:** 2026's #1 motion trend. Google's M3 Expressive update uses spring-based motion throughout. `tween` feels robotic; `spring` feels alive. Linear (the app designers love most) uses spring for everything.

### 4. 📱 Onboarding Flow (First Launch)
**What:** 3-screen first-launch walkthrough:
1. "30 formats, one app" — format icons grid
2. "Your reading, your way" — theme preview (AMOLED/Sepia/Light)
3. "Import your first file" — big gold button

**Why:** Apps with onboarding retain 25% more users after day 1. Currently Luminar opens to a cold empty library — no context, no guidance.

### 5. 🎨 Dynamic Content Cards
**What:** Book cards in the library get **format-specific visual treatments**:
- PDF: red accent corner
- EPUB: blue accent
- DOCX: blue document icon
- Code: green terminal icon
- Comic: purple splash
- Markdown: gray code bracket

**Why:** Currently all cards look identical. Format badges exist but they're tiny text. Visual coding lets users scan their library instantly.

### 6. 📐 Spacing Token Enforcement
**What:** Replace ALL remaining hardcoded dp/sp values with `Spacing.*` and `MaterialTheme.typography.*` tokens.

**Why:** The tokens exist but aren't enforced. 79 hardcoded dp values still in LibraryScreen alone. This is the difference between "has a design system" and "uses a design system."

### 7. 🔔 Haptic Choreography
**What:** Standardize haptic feedback into 3 tiers:
- **Light tap** (`TextHandleMove`): theme toggle, font change, search nav
- **Medium confirm** (`LongPress`): bookmark add, import start, delete confirm
- **None**: scrolling, page turns, passive navigation

**Why:** Currently haptics are scattered randomly. Some actions vibrate, others don't. Consistent haptic choreography makes the app feel intentional, not accidental.

---

## EXECUTION ORDER

| # | Upgrade | Files | Effort |
|---|---------|-------|--------|
| 1 | Bottom nav active indicator | LibraryScreen | ✅ DONE |
| 2 | Spring motion system | LibraryScreen, ReaderScreen | ✅ DONE |
| 3 | Reader glassmorphism | ReaderScreen, Theme.kt | ✅ DONE |
| 4 | Onboarding flow | NEW: OnboardingScreen + NavGraph + DataStore | ✅ DONE |
| 5 | Spacing token enforcement | LibraryScreen | 30 min |
| 6 | Haptic choreography cleanup | ReaderScreen, LibraryScreen | 10 min |
| 7 | Onboarding flow | NEW: OnboardingScreen | 30 min |
