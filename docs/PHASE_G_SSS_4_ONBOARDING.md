# SSS+ Upgrade 4 — First-Launch Onboarding

## What It Is

A 3-screen first-launch experience that introduces Luminar's value proposition before the user sees the empty library. Shows once, never again.

## The 3 Screens

### Screen 1: "Every format. One app."
- Gold book icon in a subtle circle
- Bold serif title with line break for drama
- Subtitle lists format breadth: PDF, EPUB, DOCX, Markdown, code, comics, spreadsheets — 30+
- Communicates: **this replaces 10 apps**

### Screen 2: "Your reading, your way."
- Palette icon
- Covers customization: themes, fonts, TTS with 7 voice profiles, bookmarks, search
- Communicates: **premium reading experience**

### Screen 3: "Built different."
- Settings/shield icon
- Privacy stance: no ads, no subscriptions, no tracking, no cloud uploads
- Communicates: **trust and respect for the user**

## Design Details

### Spring-animated page indicators
- Active page: **gold pill stretches to 28dp** with `DampingRatioMediumBouncy`
- Inactive pages: 8dp gray dots
- The stretch animation makes the indicator feel alive

### Content fade with page
- Active page content at full opacity
- Inactive pages fade to 50% during swipe
- Smooth spring interpolation

### Bottom CTA
- Pages 1-2: "Continue" — advances to next page
- Page 3: **"Get started"** — completes onboarding, navigates to library
- Gold filled button, 56dp tall, 16dp corner radius
- `Color(0xFF171100)` text on gold — high contrast

### Skip button
- Top-right corner, subtle gray text
- Skips directly to library on any page

### Navigation flow
```
App Launch
    │
    ├── First time? → Onboarding (3 screens) → Library
    │                    ↑ Skip button
    │
    └── Returning? → Library (directly)
```

### Persistence
- `hasSeenOnboarding` stored in DataStore as boolean
- Set to `true` when user taps "Get started" OR "Skip"
- NavGraph checks this on launch to determine start destination
- `popUpTo(Onboarding) { inclusive = true }` removes onboarding from backstack — user can't go "back" to it

## Why This Matters

Research from Appcues (2026): **Apps with onboarding retain 25% more users after day 1.**

Currently Luminar opens to a cold empty library with no context. The user sees "Add your first book" but doesn't know:
- What formats are supported
- That there are themes
- That there's TTS
- That there are no ads

The onboarding communicates all of this in 3 elegant swipes.

## Files

**New:**
- `presentation/onboarding/OnboardingScreen.kt` — 257 lines, 3 pages, HorizontalPager, spring indicators, page content with fade animation

**Modified:**
- `data/local/datastore/UserPreferencesRepository.kt` — `hasSeenOnboarding` field + `setOnboardingComplete()` method + `HAS_SEEN_ONBOARDING` key
- `navigation/Screen.kt` — Added `Screen.Onboarding` route
- `navigation/NavGraph.kt` — Conditional start destination, onboarding composable, `hasSeenOnboarding` + `onOnboardingComplete` params
- `MainActivity.kt` — Passes onboarding state + callback to NavGraph
