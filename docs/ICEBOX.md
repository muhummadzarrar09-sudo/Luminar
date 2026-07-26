# Icebox

Ideas that are deliberately **not** in the v1 plan. Scope creep killed the previous
version of this app — anything that isn't in the feature table of `BRAINSTORM.md` §2
lands here instead of in a sprint.

Adding something here costs nothing. Moving something out of here requires deciding
what it displaces.

## Deferred (good ideas, wrong time)

- Audiobook (M4B/MP3) playback with chapter support and Whispersync-for-Voice equivalent
- Word Wise — inline glosses over difficult vocabulary (needs a graded-vocab dataset)
- Guided view for comics — panel-by-panel navigation with pan/zoom transitions
- Two-column tablet spread + foldable posture awareness
- Handwriting / stylus annotation on PDFs (`androidx.pdf-ink` exists for this)
- Reading-group / shared-annotation features
- Desktop or web companion for annotation review
- Import from Kindle's own `My Clippings.txt`
- Wear OS companion for TTS controls
- Text reflow *inside* PDFs (extract + re-typeset scanned/fixed-layout pages)
- OCR for image-only PDFs (ML Kit text recognition)

## Explicitly rejected

- **DRM removal of any kind.** Illegal in most jurisdictions, unshippable, non-negotiable.
- **Amazon account integration / Kindle Store purchases.** No public API, ToS hostile.
- **Bundled shadow-library sources** (Z-Library, LibGen, Anna's Archive). The app ships
  legal public-domain catalogs and a generic OPDS field; what a user types into that
  field is their business.
- **On-device LLM "book insights"** (the old app's Ollama integration). Huge dependency,
  needs a server the user must run, and it was never the reason anyone opened the app.
- **Spreadsheet mode / IDE mode / code-editor rendering.** The previous version genuinely
  had planning docs for these. This is a book reader.
- **Goodreads sync.** API access is effectively closed.
- **Ads, subscriptions, telemetry, accounts.** Free, offline, private — that's the point.
