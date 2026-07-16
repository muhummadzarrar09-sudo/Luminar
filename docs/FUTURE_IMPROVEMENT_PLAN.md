# Luminar Reader — Strategic Future Improvement Plan (v2.0 & Beyond)

*Prepared on: July 14, 2026*
*Target Milestone: Luminar Reader v2.0*
*Focus: Deepening Offline-First AI, Accessibility, Synced Ecosystem, and SSS+ Engineering*

---

## Executive Summary

Luminar Reader is already an outstanding, exceptionally well-architected Android e-reader. With clean MVVM architecture, Jetpack Compose, 30+ file formats with dedicated rendering modes, robust security limits, custom TTS profiles, and a gorgeous, responsive UI (SSS+ Spring animations and Glassmorphism), it stands ready to challenge established giants like Moon+ Reader Pro and ReadEra.

To transition from an excellent independent utility to an **industry-defining powerhouse**, this plan charts the course for the next five development phases (**Phases H through L**). 

Crucially, this plan respects Luminar's key values: **offline-first privacy, zero SDK bloat, no ads, no telemetry, and pure Kotlin/Java performance.**

---

## ROADMAP OVERVIEW: PHASES H–L

```
  ┌────────────────────────────────────────────────────────┐
  │ PHASE H: Next-Gen Accessibility & RSVP Speed-Reading   │
  └───────────────────────────┬────────────────────────────┘
                              v
  ┌────────────────────────────────────────────────────────┐
  │ PHASE I: Zero-Server On-Device AI (Gemini Nano/Gemma)  │
  └───────────────────────────┬────────────────────────────┘
                              v
  ┌────────────────────────────────────────────────────────┐
  │ PHASE J: Offline Dictionary & Spaced-Repetition Cards  │
  └───────────────────────────┬────────────────────────────┘
                              v
  ┌────────────────────────────────────────────────────────┐
  │ PHASE K: Decentralized Sync & Open Annotation Exports  │
  └───────────────────────────┬────────────────────────────┘
                              v
  ┌────────────────────────────────────────────────────────┐
  │ PHASE L: Core Engine Polish & SQLite FTS5 Upgrade     │
  └────────────────────────────────────────────────────────┘
```

---

##  PHASE H: Next-Gen Accessibility & Speed-Reading (RSVP)
*Focus: Elevating TTS to audiobook standard and unlocking ultra-high-speed reading.*

### 1. Karaoke-Style Word-by-Word TTS Highlighting
Currently, TTS reads text in chunks, but the user cannot visually track which word or sentence is active.
*   **The Improvement:** Implement dynamic visual text highlighting (Karaoke-style) synced with the spoken voice.
*   **How it works (Zero-Dependencies):**
    *   Utilize Android's native `UtteranceProgressListener`.
    *   Override `onRangeStart(utteranceId, start, end, frame)`. This callback provides the precise character range being spoken in real-time.
    *   Map these character offsets back to the active Compose `AnnotatedString` in `TextReaderView`, applying a gold background/underline style to the spoken word, auto-scrolling the viewport if the reading position moves off-screen.

### 2. RSVP (Rapid Serial Visual Presentation) Speed-Reading Mode
Flashes words one-by-one in the center of the screen to eliminate eye movement, enabling reading speeds of 400–800 WPM.
*   **The Improvement:** Add a "Speed Read" overlay to the Reader screen.
*   **Design Details (Gold ORP):**
    *   Find the **Optimal Recognition Point (ORP)** for each word (typically the center-left letter, e.g., the 3rd letter of an 8-letter word).
    *   Render the word in monospace font, coloring the ORP character in gold (`#FFD700`) and the rest in standard reader text color. This anchors the user's focus.
    *   Provide controls for starting, pausing, rewinding, and adjusting WPM dynamically.

### 3. Whisper-Quiet Hands-Free Voice Controls
*   **The Improvement:** Let users navigate pages, bookmark, or pause reading entirely via short voice commands when they are reading in hands-free mode (e.g., cooking or working out).
*   **Technical Implementation:** Integrate Android's local `SpeechRecognizer` to listen for micro-phrases like *"Luminar, next"* or *"Luminar, pause"* when voice controls are enabled, bypassing external APIs entirely.

---

## PHASE I: Zero-Server On-Device AI (Gemini Nano & Local Embeddings)
*Focus: Replacing the LAN-dependent Ollama scaffold with true local AI.*

### 1. Transitioning to Google AI Edge SDK (Gemini Nano / Gemma)
Luminar's current network package contains an empty `OllamaApiService` and background worker. Because running Ollama on Android requires technical workarounds (e.g., Termux) or an active local LAN server, this is a barrier for general users.
*   **The Improvement:** Use Google's **AI Edge SDK** to run **Gemini Nano** or **Gemma-2B** directly on modern Android devices (Google Pixel 8+, Snapdragon 8 Gen 3, etc.).
*   **The Benefit:** 100% offline, lightning-fast, zero cloud costs, and perfectly matches Luminar's privacy-first core philosophy.

### 2. On-Device Semantic Search (Local Vector Indexing)
*   **The Improvement:** Search the book not just for exact keyword matching, but by *conceptual meaning* (e.g., searching for "sadness" highlights paragraphs containing "grief", "tears", and "melancholy").
*   **How to Build:**
    *   Use a lightweight local embedding model via TensorFlow Lite.
    *   Index paragraphs/chapters into small floating-point arrays.
    *   Map the embeddings to the Room database utilizing an indexing model, allowing users to query concepts instantly offline.

### 3. Dynamic "AI Book Club" Co-Pilot
Instead of just static background analysis, create a conversational assistant inside the book drawer.
*   **Features:**
    *   **Contextual Ask-AI:** Highlight any complex sentence/paragraph and select "Ask AI" from the context menu to explain it, simplify it, or translate it.
    *   **AI Character / Concept Tracker:** For dense fantasy novels or technical docs, the AI tracks recurring character names or terms and lists details dynamically.
    *   **AI Study Buddy (for PDF/Docs):** Generate 5-question multi-choice quizzes based on the chapter the user just read, checking comprehension directly inside the app.

---

## PHASE J: Offline Dictionary & Spaced-Repetition Vocabulary Builder
*Focus: Eliminating internet dependencies for word lookups and adding active learning features.*

### 1. Pure-Offline Dictionary Parsing (StarDict / MDict / Custom SQLite)
Currently, dictionary search requires `dictionaryapi.dev`. If a user is on an airplane, in the subway, or has disabled Wi-Fi to read undistracted, dictionary lookup fails.
*   **The Improvement:** Integrate a highly compressed, pre-packaged offline SQLite dictionary database or support standard StarDict/MDict catalog file imports.
*   **How it works:**
    *   Offer a lightweight (5-10MB) offline English dictionary database as an optional, lazy-loaded local download.
    *   Parse the database in Room to fetch words instantly on double-tap word selection.

### 2. Spaced-Repetition Vocabulary Builder (Luminar Cards)
*   **The Improvement:** Turn reading into active vocabulary building.
*   **How to Build:**
    *   When a user looks up a word, add a small "+" button to add it to their "Vocabulary List."
    *   Create a simple flashcard review tab in the Library screen based on **SuperMemo-2 (SM2) spaced repetition physics** (the algorithm behind Anki).
    *   Prompt the user daily with 5-10 cards they need to review, improving retention and making Luminar a vital tool for language learners and professionals.

---

## PHASE K: Decentralized Sync & Open Standards
*Focus: Resolving the #1 industry-wide pain point—cross-device sync—without proprietary servers.*

### 1. Cloud-Agnostic Nextcloud, WebDAV, Drive, and Dropbox Sync
Users do not trust proprietary reader clouds, and they hate subscriptions.
*   **The Improvement:** Build an encrypted, decentralized sync service.
*   **The Solution:**
    *   Write a unified backup coordinator that exports a lightweight, highly compressed sync file (`luminar_sync.json.gz`).
    *   Let the user connect their own storage provider: **Google Drive**, **Dropbox**, or **any WebDAV provider** (Nextcloud, Synology NAS, pCloud).
    *   Sync reading progress, bookmarks, highlights, and custom tags securely without building or hosting a custom server.

### 2. Peer-to-Peer local Wi-Fi Sharing (NearShare)
*   **The Improvement:** Send books, annotations, and reading progress directly from phone to tablet or friend without using cellular data or the internet.
*   **How to Build:**
    *   Utilize Android's **Network Service Discovery (NSD)** and a lightweight local Ktor server.
    *   When both devices open "Luminar Share" on the same network, they discover each other instantly. Tap to transfer files securely peer-to-peer.

### 3. Open Annotation Standards (Obsidian, Notion, Readwise)
*   **The Improvement:** Enable users to export their book annotations in clean, beautifully formatted Markdown files structured specifically for obsidian links or database schemas.
*   **Features:**
    *   **Obsidian / Logseq Export:** Export notes with YAML headers, blockquotes, page links, and chronological dates.
    *   **Readwise API Integration:** Add an API connection in Settings to auto-sync highlighted passages to Readwise.

---

## PHASE L: Core Engine Refinement & SQLite FTS5 Upgrade
*Focus: Maximizing parser speeds, rendering accuracy, and database search rankings.*

### 1. Room FTS4 to FTS5 Database Migration
Currently, Luminar uses FTS4 to guarantee compatibilities with old APIs. 
*   **The Refinement:** Safely upgrade Room's full-text search virtual tables to **FTS5**.
*   **Why it is safe:** Android 5.0+ (API 21+) supports FTS5. Since Luminar’s minimum supported SDK is 26, FTS5 is natively available on **100%** of your active user devices.
*   **The Huge Advantages:**
    *   **Relevance Scoring (BM25):** FTS5 supports the BM25 probabilistic relevance algorithm natively. Book search results can be sorted with the most highly matching, relevant passages first, rather than raw sequential order.
    *   **Triggers & Optimization:** Substantially cleaner tokenizers and lower storage overhead.

### 2. CSS-Aware EPUB Rendering Enhancements
The custom `EpubParser` strips HTML/CSS to flatten files into Markdown text streams. While excellent for performance, it strips formatting such as drop-caps, text alignment (centered poetry, right-aligned signatures), and styled quote blocks.
*   **The Refinement:** Enhance `EpubParser` to parse basic CSS parameters during XML streaming:
    *   Map `text-align: center` → Compose `TextAlign.Center`.
    *   Map drop-caps, custom indent margins, and block background highlights into a nested styled block model instead of a raw flat text stream.

### 3. Non-Blocking Multithreaded Import (WorkManager Pre-measure)
Currently, massive imports or unzipping large EPUBs can cause momentary frames to drop on the UI thread.
*   **The Refinement:**
    *   Offload all metadata extraction, cover generation, and text tokenization to specialized background threads orchestrated via **WorkManager**.
    *   **Pre-heating Layouts:** Pre-render and pre-measure text boundary offsets in a background coroutine before the user opens the book. When they tap a card, the view displays the content in **less than 50 milliseconds**, bypassing the layout pass.

---

## MARKETING & LAUNCH POSITIONING

Luminar’s value proposition is incredibly strong. To stand out on the Google Play Store, emphasize these exact "moats":

1.  **"30 Apps in One":** Compare standard readers (supporting ~10 formats) with Luminar's 30+ support. Emphasize that it is the *only* e-reader that natively formats Code (.kt, .py), Spreadsheets (.xlsx, .csv), Presentations (.pptx), Comics (.cbz), and E-books (.epub) differently.
2.  **"Your Data is Yours":** Emphasize: *No account registration, no analytics, no external servers, open-source-friendly Markdown annotation exports.*
3.  **"Pay Once, Own Forever":** Attack the monthly subscription fatigue. Position Luminar's $4.99 premium tier as an investment that will never badger users with upsells.

---

## EXECUTION ORDER RECOMMENDATION

For maximum impact and development ease, execute the enhancements in this chronological order:

| Step | Enhancement | Effort | Impact | Differentiator |
|---|---|---|---|---|
| **1** | **FTS4 to FTS5 Migration** | Low | High | Instant BM25 search ranking results |
| **2** | **TTS Karaoke Sync** | Medium | High | Makes voice reading feel premium |
| **3** | **Offline Dictionary (SQLite)** | Medium | High | Fixes the #1 offline reading pain point |
| **4** | **Decentralized Sync (WebDAV/Drive)** | High | Critical | Solves multi-device usage completely |
| **5** | **On-Device LLM (Gemini Nano)** | High | Extremely High | Futuristic AI integration, 100% private |
