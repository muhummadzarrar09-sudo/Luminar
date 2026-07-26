# Recto — Kindle Clone: Brainstorm & Plan

**Date:** 2026-07-26
**Status:** Planning only. No code written yet — this document is the deliverable.

**Decisions locked:**
- **Name:** Recto · package `dev.recto.reader` (see [NAMING.md](NAMING.md))
- **Platform:** Android native, Kotlin + Jetpack Compose
- **Scope:** offline-first, plus in-app free public-domain book catalogs
- **Distribution:** sideloaded APK — personal use, shareable with anyone who asks
- **License:** MIT
- **History:** clean slate; old app archived at tag `archive/luminar-v1`

---

## 1. The one-paragraph pitch

Recto is a Kindle for people who own their files. You point it at a PDF, EPUB, DOCX
or a dozen other formats and get the full Kindle experience — paginated typography,
highlights, notes, dictionary lookup, X-Ray-style character lists, reading streaks,
sync of your place across devices, TTS read-aloud, and reading-reminder notifications —
without an account, a store lock-in, or a subscription. Books you don't own yet come
from the ~78,000 free public-domain titles on Project Gutenberg and Standard Ebooks,
browsable inside the app.

**The honest boundary:** this is a Kindle *client experience* clone, not a Kindle
*content* clone. It will never open DRM-protected Amazon purchases (`.azw` with DRM,
`.kfx`). Stripping DRM is illegal in most jurisdictions and unshippable. Every format
below is DRM-free or public-domain. Say this out loud in the README so nobody
files an issue asking for it.

---

## 2. Kindle feature teardown

I went through the Kindle Android app surface by surface. Here is everything it does,
sorted by whether it earns a place in v1.

### 2.1 The reading surface — this is the whole product

| Feature | Notes | Priority |
|---|---|---|
| Paginated page turns (tap zones, swipe, animated curl/slide/fade) | The single thing that makes it *feel* like Kindle. Vertical scroll as an option. | **P0** |
| Justified text with hyphenation | Kindle's typographic edge. CSS `hyphens: auto` + `text-align: justify` in the EPUB webview. | **P0** |
| Font family / size / weight / boldness | Bookerly is Amazon-proprietary. Ship Literata, Bitter, Merriweather, Source Serif, Atkinson Hyperlegible, OpenDyslexic. | **P0** |
| Line spacing, margins, text alignment | Three-preset + fine slider, like Kindle's Aa menu. | **P0** |
| Themes: White / Sepia / Green / Black, plus true-black OLED | Kindle has exactly these four. Add auto day/night on schedule or system. | **P0** |
| Brightness + warmth slider (in-app overlay, not system) | Kindle's amber "warmth" is just an orange scrim over the page. Cheap to build, feels premium. | **P1** |
| Tap-center → chrome overlay (top bar, bottom scrubber) | Auto-hides. Immersive mode otherwise. | **P0** |
| Page scrubber with thumbnail preview + "back to page N" | The little rewind pill after you drag is a genuinely great UX detail. Copy it. | **P1** |
| "X minutes left in chapter / in book" | Computed from a rolling average of the user's own WPM. Kindle's signature stat. | **P1** |
| Location / page number / % complete toggle | Tap the footer to cycle. | **P1** |
| Continuous scroll mode | Toggle in settings. Some readers strongly prefer it. | **P1** |
| Landscape + two-column spread on tablets | | **P2** |
| Volume-key page turns | Power-user favourite, trivial to add. | **P1** |
| Screen-orientation lock, keep-screen-on | | **P1** |

### 2.2 Text interaction

| Feature | Notes | Priority |
|---|---|---|
| Long-press → select → highlight in 4 colours | | **P0** |
| Note attached to a highlight | | **P0** |
| Bookmarks (corner dog-ear) | | **P0** |
| In-book full-text search with result snippets | FTS5 index built at import time, in a background worker. | **P0** |
| Dictionary lookup on word tap | Bundle a WordNet/Wiktionary-derived SQLite dictionary (~30–60 MB) so it works offline. | **P1** |
| Wikipedia + translate tabs in the lookup card | Online-only, graceful degradation. | **P2** |
| "Notebook" — all highlights/notes for a book, exportable | Export to Markdown / CSV / clipboard. Kindle's export is email-only and terrible; beat it easily. | **P1** |
| Popular Highlights | Needs a crowd. Skip, or fake it with a personal "most revisited passages". | **Cut** |
| Share a quote as an image card | Cheap, delightful, drives word of mouth. | **P2** |

### 2.3 Library & organisation

| Feature | Notes | Priority |
|---|---|---|
| Grid / list toggle, cover art | Extract cover from EPUB OPF or render PDF page 1. Generate a typographic cover when missing. | **P0** |
| Sort: recent, title, author, progress, date added | | **P0** |
| Collections (folders, a book can be in many) | | **P1** |
| Filter: All / Downloaded / Unread / Finished / Samples | | **P1** |
| Search library by title/author/series | | **P0** |
| Series grouping + reading order | Calibre-style `series`/`series_index` metadata. | **P2** |
| Metadata editor (fix bad titles, swap cover) | Imported files have garbage metadata surprisingly often. | **P1** |
| Import: file picker, folder scan, "open with" from any app, share-sheet target | | **P0** |
| Bulk delete / archive | | **P1** |

### 2.4 Reading life / engagement

| Feature | Notes | Priority |
|---|---|---|
| **Notifications** (you asked for these — details in §6) | Daily reading reminder, streak-at-risk, goal hit, download done, TTS media controls, "continue reading" resume. | **P0/P1** |
| Reading streaks + daily goal (minutes or pages) | Kindle Insights. Strong retention hook. | **P1** |
| Stats: time read, pages, WPM, per-book history, heatmap | | **P1** |
| Achievements/badges | | **P2** |
| Home tab: "Continue reading" hero + recommendations | | **P1** |

### 2.5 Sync & multi-device

| Feature | Notes | Priority |
|---|---|---|
| Whispersync — furthest page read, across devices | v1: export/import a JSON backup. v2: sync via the user's own Google Drive / WebDAV / Nextcloud app-folder. No server of mine to run. | **P1 → P2** |
| Send-to-Recto by email | Needs a server. | **Cut for v1** |
| Cloud library | Same. | **Cut for v1** |
| Full local backup/restore (library DB + annotations + settings) | Must-have regardless. | **P0** |

### 2.6 Extras

| Feature | Notes | Priority |
|---|---|---|
| Text-to-speech read-aloud with media-notification controls | Android `TextToSpeech` + `MediaSession`. Sentence highlighting as it reads. | **P1** |
| Audiobook (M4B) playback + Whispersync-for-Voice | Big separate subsystem. | **P3** |
| X-Ray (characters, terms, places with first-mention jumps) | Doable *offline* with a simple NER pass over the extracted text at import. No LLM required. | **P2** |
| Word Wise (inline simple-word glosses above hard words) | Needs a graded-vocabulary dataset. Neat, niche. | **P3** |
| Vocabulary Builder (flashcards from dictionary lookups) | Falls out almost free once dictionary lookup exists. | **P2** |
| Goodreads integration | Third-party API friction. | **Cut** |
| Comics/manga guided view (CBZ/CBR panel-by-panel) | | **P2** |

### 2.7 Explicitly out of scope

DRM removal · Amazon account login · Kindle Store purchases · Kindle Unlimited ·
piracy-oriented sources (Z-Library, Anna's Archive, LibGen) — the app ships with
legal public-domain catalogs and *user-added* OPDS URLs, and takes no position on
what the user types in.

---

## 3. Format support strategy

The old app hand-rolled its own EPUB parser and it did not go well (its own audit doc
was literally named `READING_ENGINE_BIRTH_DEFECTS_AUDIT.md`). **Do not write a parser
this time.** Stand on Readium, the toolkit behind 100+ shipping reading apps
[[Readium Mobile]](https://readium.org/mobile/).

| Format | Engine | Effort |
|---|---|---|
| **EPUB 2 / 3** | Readium Kotlin Toolkit `readium-navigator-epub` (v3.2.0, May 2026 — now has animated page transitions [[release note]](https://blog.readium.org/release-note-kotlin-toolkit-version-3-2-0/)) | Low — it's a solved problem |
| **PDF** | `androidx.pdf` (Jetpack, alpha19 as of July 2026, backported to minSdk 28 [[androidx.pdf releases]](https://developer.android.com/jetpack/androidx/releases/pdf)) — with `PdfRenderer` as the always-available fallback | Medium — alpha API, pin the version |
| **DOCX** | Convert on import → HTML → wrap as EPUB → hand to Readium. Apache POI is far too heavy for Android; use a focused OOXML→HTML converter or a trimmed POI-OOXML subset. | Medium-high — see risk R2 |
| **MOBI / AZW3** (DRM-free) | Parse to HTML on import, wrap as EPUB. Readium doesn't cover these. | Medium |
| **TXT / MD / HTML / RTF / FB2** | Normalise to HTML on import, wrap as EPUB | Low |
| **CBZ / CBR** | Readium has a comics navigator; CBR needs a RAR decoder (junrar) | Low-medium |
| **DjVu** | Rare. Skip unless asked. | Cut |

**The key architectural idea: one internal format.** Everything that is fundamentally
reflowable text (DOCX, MOBI, TXT, MD, HTML, RTF, FB2) gets converted **once, at import
time, in a background worker**, into a canonical EPUB stored in app-private storage.
The reader then only ever knows two engines: *Readium* (reflowable + comics) and
*PDF* (fixed layout). That collapses N reader implementations into 2, and every
typography, highlight, TTS and search feature written for EPUB works for all of them
for free. This is the single most important decision in this document.

Original files stay untouched where the user put them; converted copies live in
`filesDir/library/<uuid>/`.

---

## 4. Architecture

### 4.1 Stack

- **Kotlin 2.x**, **Jetpack Compose** + Material 3 (expressive), single-Activity
- **Clean-ish architecture**: `feature/*` modules → `domain` → `data`. Multi-module
  from day one, because Compose build times on a monolith get miserable fast.
- **Hilt** for DI, **Room** (+ FTS5) for the library and annotations,
  **DataStore Proto** for settings, **WorkManager** for import/convert/index/backup,
  **Coil** for covers, **Ktor Client** (or Retrofit) for OPDS + downloads.
- **minSdk 28, targetSdk 36.** minSdk 28 is what `androidx.pdf`'s backport supports and
  covers effectively everyone.
- Testing: JUnit5 + Turbine + Robolectric; Compose UI tests for the reader gestures;
  a **format-corpus test** — a folder of nasty real-world files that every parser
  change must survive.

### 4.2 Module map

```
:app                       single Activity, nav host, DI wiring
:core:designsystem         theme, tokens, typography, reusable composables
:core:ui                   shared Compose widgets
:core:database             Room entities, DAOs, FTS, migrations
:core:datastore            proto settings
:core:common               dispatchers, Result, logging
:core:notifications        channels, scheduling, media session
:feature:library           grid/list, collections, sort/filter, metadata editor
:feature:reader            the reading surface — Readium + PDF navigators
:feature:annotations       highlights, notes, notebook, export
:feature:catalog           Gutendex / Standard Ebooks / OPDS browse + download
:feature:stats             streaks, goals, heatmap
:feature:settings          everything configurable
:feature:tts               read-aloud + media notification
:format:*                  epub, pdf, docx, mobi, text, comic → converters/probes
```

### 4.3 Data model (first cut)

```
Book(id, title, authors, series, seriesIndex, language, publisher, publishedAt,
     coverPath, originalUri, canonicalPath, format, sizeBytes, wordCount,
     addedAt, lastOpenedAt, isFinished, isArchived)
ReadingPosition(bookId, locator /* Readium JSON locator */, progressFraction,
                pageIndex /* PDF */, updatedAt, deviceId)
Annotation(id, bookId, type /* highlight|note|bookmark */, locator, selectedText,
           note, colour, createdAt, updatedAt)
Collection(id, name, sortIndex)  +  BookCollectionCrossRef(bookId, collectionId)
ReadingSession(id, bookId, startedAt, endedAt, msRead, wordsRead)
SearchIndex  -- FTS5 virtual table (bookId, chapterHref, text)
DictionaryLookup(id, word, definition, bookId, contextSentence, createdAt)
CatalogSource(id, name, type /* gutendex|opds */, url, enabled)
```

Room schemas exported and version-controlled from commit one; every migration gets a
test. The old app had schema files for v1, v2 and v9 with the middle ones missing —
that's how you end up wiping user libraries on upgrade.

### 4.4 The reader abstraction

```kotlin
interface ReaderEngine {
    val publication: PublicationMeta
    fun open(book: Book): Result<ReaderSession>
}

interface ReaderSession {
    val locator: StateFlow<Locator>
    val toc: List<TocEntry>
    suspend fun go(target: Locator, animated: Boolean)
    suspend fun next(); suspend fun previous()
    fun applyTypography(settings: TypographySettings)   // no-op for PDF
    suspend fun search(query: String): Flow<SearchHit>
    suspend fun highlight(range: Locator, colour: HighlightColour)
    fun extractTextForTts(from: Locator): Flow<Utterance>
}
```

Two implementations: `ReadiumReaderSession`, `PdfReaderSession`. The Compose reader
screen talks only to this interface, so PDF and EPUB share one chrome, one settings
sheet, one annotation flow, one TTS controller. `applyTypography` being a no-op on PDF
is the only visible seam, and the settings sheet just hides the irrelevant controls.

---

## 5. Getting books into the app

Three doors, all first-class:

1. **Your files.** Storage Access Framework picker (single + multi-select), a
   watched-folder scan (e.g. `/Download`, `/Documents/Books`), plus manifest entries
   so Recto appears in **"Open with"** and the **share sheet** for every supported
   MIME type. Dropping a file on the app should Just Work.

2. **Free catalogs, browsable in-app:**
   - **Gutendex** — free JSON API over Project Gutenberg, ~78k public-domain books,
     no API key, no rate limit, returns EPUB/HTML/TXT download URLs directly
     [[Gutendex]](https://github.com/garethbjohnson/gutendex). Ideal default source.
   - **Standard Ebooks** — same public-domain texts but beautifully typeset, with a
     proper OPDS feed. The quality tier.
   - **Open Library / Internet Archive** — metadata enrichment and cover art.
   - **Generic OPDS 1.2 / 2.0 client** — one screen, and the user can add any feed
     they like, including their own **Calibre-Web** or **Kavita** server at home.
     This is how "get books" stays open-ended and legal without me curating anything.

3. **Sideload / desktop transfer.** Calibre users just point the folder scan at their
   Calibre library. Optionally read `metadata.opf` next to a file for good metadata.

Downloads go through WorkManager: resumable, Wi-Fi-only toggle, queue UI, notification
on completion.

---

## 6. Notifications — the design

You called these out specifically, so here is the full plan. Android 13+ needs a
runtime `POST_NOTIFICATIONS` request, asked *contextually* (when the user first sets a
goal or starts TTS), never on first launch.

**Channels** (separate, so each is individually mutable):

| Channel | Importance | Fires |
|---|---|---|
| `reading_reminders` | Default | "Your daily reading time — 20 min in *Dune*" at a user-chosen time. Suppressed if the goal is already met. |
| `streaks_goals` | Default | Streak at risk ("2 hours left to keep your 14-day streak"), goal reached, weekly recap on Sunday. |
| `downloads` | Low | Download progress + completion, tap → open the book. |
| `tts_playback` | Low, ongoing | `MediaStyle` foreground notification: play/pause, ±30s, chapter skip, speed, sleep timer. Lock-screen + Bluetooth + Android Auto controls. |
| `library` | Low | "Import finished: 12 books added", conversion failures with a "why?" action. |
| `system` | Min | Backup completed, storage warnings. |

**Scheduling:** `AlarmManager.setExactAndAllowWhileIdle` for the daily reminder (needs
`SCHEDULE_EXACT_ALARM` handling on 12+ — or accept inexact and dodge the permission),
WorkManager for everything else. Reboot receiver to re-arm. All of it must respect a
global quiet-hours window and a master "notifications off" switch.

**Extras worth building:** a "continue reading" resume notification if you closed a
book mid-chapter under 30 minutes ago; a widget + quick-settings tile that jumps
straight into the current book.

**The rule:** never more than one engagement notification a day. Reading apps that
nag get uninstalled. Every single one is off-by-default except downloads and TTS.

---

## 7. UI / UX plan

Kindle's UI is deliberately plain — the book is the interface. Match that restraint,
then modernise the chrome.

**Navigation:** bottom bar with **Home · Library · Catalog · More**. Kindle uses
Home/Library/More; Catalog earns its slot here because acquiring books is a core
flow for us.

- **Home** — "Continue reading" hero card with cover, progress ring and time-left;
  a streak strip; recently added; catalog suggestions.
- **Library** — grid (2/3/4 columns) or list, cover-forward, progress bar on each
  spine, long-press for multi-select, collections as chips across the top.
- **Reader** — full-bleed page, zero chrome. Tap left/right thirds to turn, tap centre
  for chrome. Top bar: back, search, bookmark, TOC, Aa, overflow. Bottom: scrubber +
  location/time-left. `Aa` sheet has tabs *Font · Layout · Theme · More*, exactly like
  Kindle's.
- **Notebook** — highlights and notes per book, filter by colour, export button.
- **Motion:** page turns 220–280 ms, curl/slide/fade selectable, honour
  "reduce animations". Everything else is Material 3 standard.
- **Accessibility is not optional:** TalkBack labels on every control, dynamic type
  respected, OpenDyslexic + Atkinson Hyperlegible bundled, 4.5:1 contrast on all four
  themes, full keyboard/D-pad navigation for hardware keyboards and page-turner remotes.

---

## 8. Roadmap

Nine phases. Each ends with something installable on your phone.

| Phase | Goal | You can... |
|---|---|---|
| **0 — Foundation** (1 wk) | Empty multi-module project, Gradle version catalog, CI (build + lint + test on PR), design system skeleton, Room + DataStore wired, debug APK on device | ...install a blank app |
| **1 — Read an EPUB** (2 wk) | Readium integrated, SAF import, Room library, grid, reader with tap/swipe turns, position saved & restored | ...actually read a book cover to cover |
| **2 — Make it feel like Kindle** (2 wk) | Typography engine, 4 themes + OLED, brightness/warmth, page-turn animations, TOC, scrubber + thumbnails, time-left, volume keys, immersive chrome | ...forget it isn't Kindle |
| **3 — PDF + all formats** (2–3 wk) | `androidx.pdf` reader; the convert-to-EPUB pipeline for DOCX/MOBI/TXT/MD/HTML/RTF/FB2; CBZ/CBR; format-corpus test suite | ...open literally anything you own |
| **4 — Annotations & search** (2 wk) | Highlights ×4 colours, notes, bookmarks, notebook + export, FTS5 in-book and library-wide search | ...study, not just read |
| **5 — Notifications & habits** (1–2 wk) | All channels from §6, reminders, streaks, goals, stats screen, widget, quick-settings tile | ...build a reading habit |
| **6 — Getting books** (2 wk) | Gutendex browse/search, Standard Ebooks, generic OPDS, resumable download manager | ...get 78k books free, in-app |
| **7 — Voice & smarts** (2 wk) | TTS with sentence highlighting + media notification + sleep timer, offline dictionary, vocabulary builder, X-Ray | ...listen, and look words up on a plane |
| **8 — Sync & polish** (2 wk) | Backup/restore, Drive/WebDAV position sync, tablet two-column, perf pass (cold start < 1.5 s, 60 fps turns on a mid-range phone), a11y audit, signed release APK | ...run it on every device you own |

**Realistic total: ~4 months of steady evenings** to a genuinely Kindle-competitive
app. Phases 0–2 (~5 weeks) already give you something you'd use daily.

---

## 9. Risks & how to defuse them

| # | Risk | Mitigation |
|---|---|---|
| R1 | `androidx.pdf` is still **alpha** (alpha19, July 2026) — APIs churn between releases | Pin the exact version; hide it behind `PdfReaderSession`; keep a bare-`PdfRenderer` fallback path that always compiles |
| R2 | **DOCX conversion** is the hardest format. Apache POI is huge and Android-hostile | Time-box a spike in Phase 3. Fallback ladder: focused OOXML→HTML converter → POI-OOXML-lite → worst case, render DOCX text-only and be honest about lost formatting |
| R3 | **Large PDFs** (500 MB scanned tomes) blow up memory | Page-level bitmap cache with an LRU budget tied to `ActivityManager.memoryClass`; render off the main thread; never hold more than ±2 pages |
| R4 | **FTS indexing** of a 200-book library hammers the battery | WorkManager with `requiresBatteryNotLow` + `requiresCharging` for bulk backfill; index incrementally on import; chunk with progress |
| R5 | **Scope creep** — exactly what killed v1 (spreadsheet mode, IDE mode, an on-device LLM) | The table in §2 is the contract. Anything not in it goes to `docs/ICEBOX.md`, not into the sprint. No LLM in v1. |
| R6 | **DRM requests** from users | Documented as out of scope in the README, closed as wontfix |
| R7 | **Readium learning curve** (locators, services, decorations are unusual concepts) | Read the Test App source in the kotlin-toolkit monorepo before Phase 1; budget 2–3 days of ramp |
| R8 | **Android 15/16 background limits** break reminders | Prefer WorkManager; treat exact alarms as a nice-to-have; test on a Xiaomi/Samsung with aggressive battery killers, which is where reminder apps actually die |
| R9 | **Font licensing** | Ship only OFL/Apache fonts (Literata, Bitter, Merriweather, Source Serif, Atkinson Hyperlegible, OpenDyslexic). Never Bookerly. |

---

## 10. Open questions

### Answered ✅

1. ~~**Name**~~ → **Recto**, package `dev.recto.reader`. "Luminar" collided with Skylum's
   Luminar Neo photo editor; full reasoning and the rejected shortlist in [NAMING.md](NAMING.md).
2. ~~**Audience**~~ → Personal sideload, shared with anyone who asks. **No Play Store work
   in v1**: skip the privacy policy, data-safety form, store assets and content rating.
   The architecture stays Play-compatible so publishing later is a packaging job, not a
   rewrite — which is exactly why the name had to change now.
3. ~~**License**~~ → **MIT**. `LICENSE` is committed. Readium is BSD-3 and all bundled
   fonts are OFL/Apache-2.0, so everything is compatible.

### Still open (none of these block Phase 0)

4. **Tablet & foldable support in v1**, or phone-only first?
   *Leaning phone-only, with layouts written adaptively so tablets are a Phase 8 nicety.*
5. **Sync target:** Google Drive app-folder (easiest, Google-locked) vs WebDAV/Nextcloud
   (self-hosted) vs plain export/import file (dumbest, most reliable).
   *Leaning export/import in v1, WebDAV later — no server for me to run either way.*
6. **Offline dictionary:** 30–60 MB. Bundle in the APK, or download on first use?
   *Leaning optional download — keeps the sideloaded APK small.*
7. **Audiobooks (M4B) — ever?** If yes, design the media layer for it in Phase 7 rather
   than retrofitting.
8. **Salvage anything from the old app?** `COMPETITOR_RESEARCH.md`, `FORMAT_AUDIT.md` and
   the design-system work are all in `archive/luminar-v1`.
   *My take: reuse the research, none of the code.*

---

## 11. Immediate next step

All three blocking questions are answered, so **Phase 0 is ready to scaffold**:
multi-module Gradle project, version catalog, CI workflow, design system, and an
installable debug APK.

**One caveat about how this gets built.** The environment these docs were authored in
has no JDK, no Android SDK and no network access to `dl.google.com`, so I can write the
Gradle files but *cannot compile or verify them here*. The first `./gradlew assembleDebug`
has to happen on your machine, and realistically a version pin or two will need fixing on
that first run. Two ways to handle it:

- **(a)** I write the full Phase 0 scaffold now; you run it and paste any errors back.
- **(b)** You create the project skeleton in Android Studio (which generates a guaranteed-
  working wrapper, SDK paths and `local.properties`), push it, and I build every feature
  on top of a foundation that's known to compile.

**(b) is lower-friction if you have Android Studio installed.** (a) is fine if you'd
rather I do everything and don't mind a round-trip of build fixes.

### Verified versions for Phase 0 (July 2026)

| Component | Version | Note |
|---|---|---|
| AGP | 9.3.0 | requires Gradle 9.5.0, JDK 17, Build Tools 36 |
| Kotlin | 2.3.x | Compose compiler plugin version must match exactly |
| Compose BOM | 2026.06.01 | Material3 1.4.0 stable |
| compileSdk / targetSdk | 36 | Compose 1.12+ will later force compileSdk 37 + AGP 9 |
| minSdk | 28 | matches the `androidx.pdf` backport floor |
| Readium | 3.2.0 | animated EPUB page transitions, May 2026 |
| androidx.pdf | 1.0.0-alpha19 | **alpha — pin it**, see risk R1 |

---

### Appendix: what was here before

`archive/luminar-v1` (commit `70e8239`) holds the previous app: Kotlin/Compose,
Room v9, Hilt, a hand-written EPUB parser, `android-pdf-viewer`, a TTS service, an
Ollama LLM integration for "book insights", plus 46 planning docs and a stray 190 KB
`luminar_improvements.patch`. Retrieval commands are in the README.
