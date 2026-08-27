# Phase 5 — Lookup (dictionary + Wikipedia) and reader navigation

Two things, scoped separately because one is a small UI job and the other is a
real feature with a hard decision in the middle.

---

## Part A — Reader top bar: overflow menu

### The problem

The bar is currently `Back · Title · Mark · Notes · TOC · Aa`. Four text
buttons plus a back button leaves the title squeezed into whatever is left,
and on a narrow phone that is roughly nothing. The title is the one thing that
should be readable — it tells you what you are reading.

### The fix

```
[<]  The Hobbit                              Aa   [⋮]
```

- **Back** — icon only, left
- **Title** — takes all remaining width, `bodyMedium` instead of `labelSmall`,
  ellipsised. Chapter name underneath in small text when the TOC gives us one.
- **Aa** — stays out. It is the single most-used control while reading and
  burying it behind a menu would be a downgrade.
- **⋮ overflow** — everything else:
  - Bookmark this page / Remove bookmark (toggles label to match state)
  - Contents
  - Notebook
  - Search in book *(greyed until Part C exists)*
  - Book details

**Why Aa stays visible:** the test for "does this belong in the overflow" is
whether you touch it mid-chapter. Text size and theme, constantly. TOC and
notebook, rarely. Kindle keeps Aa on the bar for exactly this reason.

**Effort: half a day.** No new data, no new dependencies.

---

## Part B — Lookup

Tap a word → a card slides up with its definition. Swipe across to Wikipedia.
This is Kindle's single best feature and the main reason reading on a phone
beats paper for anything with unfamiliar vocabulary.

### The hard decision: offline or online?

This is the question that shapes everything else, and it is a genuine
trade-off rather than an obvious call.

| | **Online API** | **Bundled offline** |
|---|---|---|
| APK size | +0 MB | +25–60 MB |
| Works on a plane / in bed with wifi off | ✗ | ✓ |
| Speed | 200–800 ms | Instant (<10 ms) |
| Build effort | ~1 day | ~3–4 days |
| Coverage | Excellent (full Wiktionary) | Good (WordNet ~150k words) |
| Fails when | No signal, API down | Never |

**Both endpoints verified live today:**

- `api.dictionaryapi.dev/api/v2/entries/en/{word}` — no key, no signup.
  Returns definitions grouped by part of speech, IPA pronunciation, synonyms,
  antonyms, and **audio pronunciation URLs**. Wiktionary-sourced, CC BY-SA.
- `en.wikipedia.org/api/rest_v1/page/summary/{title}` — no key. Returns a
  clean 2–3 sentence extract plus a thumbnail. 500 req/hour per IP anonymous,
  which is far beyond what a reader would ever hit.

**My recommendation: ship online first, add offline as a download later.**

Online gets the feature working end to end in a day, and the plumbing —
lookup card, word tap, vocabulary list, caching — is identical either way. Then
offline becomes a swap of one implementation behind an interface, plus an
optional download in Settings for people who read on planes.

Doing offline first means three days before you can look up a single word, and
a 60 MB APK for a feature you might decide you do not use.

**The compromise that makes online feel offline:** cache every lookup in Room.
Words repeat constantly within a book — an author who uses "peregrination"
once uses it four times. After the first hit it is instant and works with no
signal.

### What the card shows

```
┌─────────────────────────────────┐
│ serendipity          /ˌsɛɹənˈdɪpɪti/  🔊 │
│ ─────────────────────────────── │
│ noun                            │
│ 1. A combination of events which│
│    have come together by chance │
│    to make a surprisingly good  │
│    outcome.                     │
│ 2. An unsought, unintended...   │
│                                 │
│ Synonyms: chance, luck          │
│ ─────────────────────────────── │
│ [Dictionary] [Wikipedia] [Save] │
└─────────────────────────────────┘
```

- Tabs, not a scroll: **Dictionary** and **Wikipedia**, swipeable.
- 🔊 plays the pronunciation audio when the API supplies one.
- **Save** adds it to a vocabulary list.
- Compact by default, drag up for the full entry.

### Interaction

Tapping a word currently does nothing (taps are page turns / chrome toggle).
Options:

1. **Single tap on a word → lookup.** Kindle's behaviour. Risks conflicting
   with the tap-to-turn zones.
2. **Long-press → selection toolbar gains a "Define" button.** Zero conflict,
   one extra tap.
3. **Both:** long-press always works; single tap in the *centre* third looks
   up the word under it, since the centre currently only toggles chrome.

**Recommendation: 3.** Outer thirds keep turning pages, the centre becomes
useful, and long-press stays as the reliable path.

### Vocabulary list

Falls out nearly free once lookups are cached. A screen listing saved words
with their definitions and the sentence you met them in. Kindle's Vocabulary
Builder, minus the spaced repetition.

If you want flashcards later, SM-2 is about a day on top.

### Effort

| Piece | Time |
|---|---|
| Lookup card UI + tabs | 1 day |
| Dictionary API + Room cache | 0.5 day |
| Wikipedia API | 0.5 day |
| Word-tap plumbing | 0.5 day |
| Vocabulary list screen | 0.5 day |
| **Total** | **~3 days** |

Offline dictionary, if we do it: **+3 days** and +25 MB, as an optional
in-app download rather than bundled in the APK.

---

## Part C — Search in book (not scoped yet)

Mentioned because the overflow menu should have a slot for it. Full-text
search across the open book, then across the library, using SQLite FTS5.
Roughly 2 days. Say the word and I will scope it properly.

---

## Recommended order

1. **Part A** — half a day, immediately makes the reader feel less cluttered
2. **Part B online** — 3 days, the feature you actually asked for
3. Offline dictionary or search, depending on which you miss more

---

## Open questions

1. **Online-first, or hold out for offline?** My vote is online-first with
   caching, offline later as a download.
2. **Word-tap gesture:** option 3 above, or long-press only?
3. **Which dictionary?** `dictionaryapi.dev` is free, keyless and Wiktionary-
   backed — good coverage, occasionally terse. Merriam-Webster has a free tier
   with better prose but needs an API key and signup.
4. **Translation tab?** Kindle has one. Needs a translation API, and the free
   ones are rate-limited or unreliable. Skip for now?
5. **Vocabulary flashcards** with spaced repetition, or just a saved-words
   list to start?
