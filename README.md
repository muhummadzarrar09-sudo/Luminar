# Recto

**The page you're on.**

A Kindle-class ebook reader for Android. Read PDF, EPUB, DOCX and more on your own
phone — offline, free, no account, no ads, no tracking.

> **Status: planning.** The repo was reset to a clean slate on 2026-07-26. There is no
> application code yet, by design. Start with [`docs/BRAINSTORM.md`](docs/BRAINSTORM.md).

---

## What this is going to be

| | |
|---|---|
| **Platform** | Android native — Kotlin + Jetpack Compose |
| **Distribution** | Sideloaded APK. Built for one phone, shareable with anyone who asks. |
| **Cost** | Free. MIT licensed. No subscriptions, no ads, no accounts, no telemetry. |
| **Books in** | Your own files + free public-domain catalogs (Project Gutenberg, Standard Ebooks, any OPDS feed) |
| **Formats** | EPUB, PDF, DOCX, MOBI/AZW3 (DRM-free), TXT, MD, HTML, RTF, FB2, CBZ/CBR |
| **Offline** | Everything works in airplane mode. Network is only for fetching new books. |

**Not in scope:** DRM removal, Amazon account login, Kindle Store purchases. This is a
clone of the Kindle *reading experience*, not of Amazon's *content pipeline*. Every
supported format is DRM-free or public domain.

## Documentation

| Doc | What's in it |
|---|---|
| **[docs/BRAINSTORM.md](docs/BRAINSTORM.md)** | The plan. Kindle feature teardown, architecture, format strategy, notification design, UI, 9-phase roadmap, risks. **Read this first.** |
| [docs/BUILD_SETUP.md](docs/BUILD_SETUP.md) | How to build: the scripts, the AGP 9 / KSP trap, adb setup, version pins |
| [docs/NAMING.md](docs/NAMING.md) | Why "Recto", and the names that were rejected (and why "Luminar" had to go) |
| [docs/ICEBOX.md](docs/ICEBOX.md) | Deferred and explicitly rejected scope — the anti-scope-creep contract |

## Scripts

```powershell
# Run this first. Read-only, changes nothing, writes doctor-report.txt.
powershell -ExecutionPolicy Bypass -File .\scripts\recto-doctor.ps1

# Build + install to phone + tail logs (once Phase 0 exists).
powershell -ExecutionPolicy Bypass -File .\scripts\recto-build.ps1

# Verify the scripts are ASCII-clean and parse (they must stay ASCII).
powershell -ExecutionPolicy Bypass -File .\scripts\check-ascii.ps1
```

See [docs/BUILD_SETUP.md](docs/BUILD_SETUP.md) for details.

## Roadmap at a glance

| Phase | Delivers |
|---|---|
| 0 | Project skeleton, CI, design system, installable blank APK |
| 1 | Read an EPUB end to end — import, library, reader, saved position |
| 2 | Kindle feel — typography, 4 themes, page-turn animations, time-left |
| 3 | PDF + every other format via the convert-to-EPUB pipeline |
| 4 | Highlights, notes, bookmarks, notebook export, full-text search |
| 5 | Notifications, streaks, goals, stats, widget |
| 6 | In-app free book catalogs + download manager |
| 7 | TTS read-aloud, offline dictionary, X-Ray |
| 8 | Backup/sync, tablet layout, perf + a11y pass, signed release |

Phases 0–2 (~5 weeks of evenings) already produce a daily driver.

## Build

Nothing to build yet. Once Phase 0 lands:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK 36. **Note:** the project is authored in an environment
without a JDK or Android SDK, so the first real compile happens on your machine —
expect to fix a version pin or two on the first run.

## The previous app

This repo previously held *Luminar Reader*, a working-but-tangled Android e-reader
(~55 Kotlin/resource files, 46 planning docs, an Ollama LLM integration, and a stray
190 KB patch file). Nothing was lost — it's preserved in git history:

```bash
git show archive/luminar-v1 --stat                    # browse the old tree
git show archive/luminar-v1:docs/COMPETITOR_RESEARCH.md   # read one old file
git checkout archive/luminar-v1 -- docs/FORMAT_AUDIT.md   # restore one file
git checkout archive/luminar-v1 -- .                  # restore the whole old app
```

Old commit: `70e8239`, tagged `archive/luminar-v1` (pushed to GitHub).

**Why the reset:** the old codebase had drifted into spreadsheet and IDE "rendering
modes", depended on a local LLM server, spread its plan across 46 overlapping phase
docs, and had a hand-rolled EPUB parser whose own audit document was titled
*"Birth Defects"*. Rebuilding on Readium — the toolkit behind 100+ shipping reading
apps — is faster than untangling that.

## License

[MIT](LICENSE) © 2026. Readium (BSD-3) and the bundled fonts (OFL/Apache-2.0) carry
their own compatible licenses.
