# Luminar

A Kindle-class ebook reader for Android. Read PDF, EPUB, DOCX and more on your own
phone — offline, free, no account required.

> **Status: clean slate.** This repository was reset on 2026-07-26. There is no code
> yet, by design. The current deliverable is the plan in [`docs/BRAINSTORM.md`](docs/BRAINSTORM.md).

---

## What this is going to be

| | |
|---|---|
| **Platform** | Android native — Kotlin + Jetpack Compose |
| **Distribution** | Sideloaded APK on your own phone (Play Store optional, later) |
| **Cost** | Free. No subscriptions, no ads, no accounts. |
| **Books in** | Your own files + free public-domain catalogs (Project Gutenberg, Standard Ebooks, any OPDS feed) |
| **Formats** | EPUB, PDF, DOCX, MOBI/AZW3 (DRM-free), TXT, MD, HTML, RTF, FB2, CBZ/CBR |
| **Offline** | Everything works with the radio off. Network is only for downloading books. |

## Documentation

- **[docs/BRAINSTORM.md](docs/BRAINSTORM.md)** — the full plan: Kindle feature teardown,
  architecture, library choices, phased roadmap, risks, open questions. **Start here.**

## The previous app

This repo previously held *Luminar Reader*, a working-but-tangled Android e-reader
(≈55 Kotlin/resource files, 46 planning docs, an Ollama LLM integration, and a
190 KB stray patch file). Nothing is lost — it is preserved in git history:

```bash
# browse the old tree
git show archive/luminar-v1 --stat
git ls-tree -r archive/luminar-v1 --name-only

# read a single old file without restoring it
git show archive/luminar-v1:docs/COMPETITOR_RESEARCH.md
git show archive/luminar-v1:app/src/main/java/com/luminar/reader/data/epub/EpubParser.kt

# restore one file into the new tree
git checkout archive/luminar-v1 -- docs/FORMAT_AUDIT.md

# restore the entire old app
git checkout archive/luminar-v1 -- .
```

The old commit is `70e8239`. The tag `archive/luminar-v1` points at it.
Push the tag if you want it on GitHub: `git push origin archive/luminar-v1`.

## Why the reset

The old codebase carried a lot of accumulated drift — a scope that wandered into
spreadsheet and IDE "rendering modes", an on-device LLM dependency, 46 overlapping
phase documents, and a reading engine whose own audit doc was titled
*"Birth Defects"*. Rebuilding on a proven ebook engine (Readium) instead of a
hand-rolled parser is faster than untangling it.

## License

TBD — see open questions in the brainstorm.
