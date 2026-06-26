# Luminar — Phase 1 Documentation

## Executive Summary
Phase 1 established the foundation of Luminar, a production-grade Android reading application built with modern Android development standards (Kotlin, Jetpack Compose, Material3, Room, and Hilt). The primary objective of Phase 1 was delivering a highly responsive, performant PDF reader with robust local library management and reader customization.

## What Was Built
- **Local PDF Library**: Grid-based UI displaying imported PDFs with rendered front-cover thumbnails and reading progress strips. Built-in detection for missing local files with removal prompts.
- **PDF Reader**: Full-featured PDF reader using `android-pdf-viewer` wrapped in a Compose `AndroidView`, supporting horizontal swipe page snapping, double-tap zoom, and custom background tinting.
- **Three Reader Themes**: 
  - `DARK_AMOLED`: Pure black AMOLED background (`#000000`) with high-contrast text and PDF inverted night mode.
  - `SEPIA`: Warm, low-eye-strain sepia background (`#F4ECD8`) with muted brown UI accents.
  - `LIGHT`: Clean white background with subtle grey containers.
- **Progress Persistence**: Room database persistence tracking current page index, scroll offset, and last read timestamp per book. Automatically restores reading state upon re-opening.
- **Hardware & Display Controls**:
  - **Volume Button Navigation**: Hardware volume keys mapped to page turn commands via `ReaderInputController`.
  - **Screen Keep-Alive**: Configurable `FLAG_KEEP_SCREEN_ON` window flag toggled via DataStore user preferences.
- **AI & Integration Stubs**:
  - **Ollama API Service**: Retrofit service definitions (`OllamaApiService`) and `BookAnalysisWorker` stubs prepared for local LLM book summarization and Q&A.
  - **Share Intent & App Icon**: Proper Android manifest configurations for viewing external PDF documents and custom app branding.

## Stack Decisions Made
- **Kotlin 2.2.x & AGP 9.1.x**: Leveraging modern Kotlin compiler advancements and stable Gradle toolchains.
- **Jetpack Compose + Material3**: Declarative UI paradigm ensuring reactive state rendering and modern Material Design compliance.
- **Hilt & KSP**: Compile-time dependency injection using Kotlin Symbol Processing (KSP) for optimal build speeds.
- **Room Database**: Local SQLite abstraction ensuring ACID compliance and seamless Flow observability.
- **Preferences DataStore**: Asynchronous, transactional key-value storage replacing legacy SharedPreferences.

## Known Limitations Going Into Phase 2
1. **Single Format Limitation**: Only supports PDF files (`application/pdf`). No support for reflowable EPUB ebooks.
2. **Missing Navigation Aids**: No Table of Contents (TOC) sidebar or bookmark jumping mechanism.
3. **No In-Book Search**: Lacks text extraction and full-text search capabilities across document contents.
4. **No Zoom Persistence**: Reader resets zoom level to default (1.0x) upon exiting and re-opening a book.
5. **No Reading Metrics**: Lacks active session time tracking or daily/weekly reading time analytics.
