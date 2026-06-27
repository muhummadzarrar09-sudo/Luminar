# Technical Architecture & Library Decisions

## Overview
This document records the rationale behind critical architectural and dependency selections in the Luminar Android project.

## 1. `mhiew/android-pdf-viewer` over `barteksc`
- **Context**: The original `barteksc/AndroidPdfViewer` library is widely used but has been unmaintained/archived for several years, leading to incompatibility issues with newer Android SDKs and modern build toolchains.
- **Decision**: Adopted `com.github.mhiew:android-pdf-viewer:3.2.0-beta.3`.
- **Rationale**: The `mhiew` fork actively maintains the codebase, provides critical updates for Android API 34+, resolves underlying PDFium memory leaks, and integrates cleanly within modern Jetpack Compose `AndroidView` wrappers.

## 2. Readium2 over FolioReader
- **Context**: Adding reflowable EPUB support requires an ebook rendering engine.
- **Decision**: Selected Readium2 (Kotlin Toolkit) over FolioReader.
- **Rationale**: FolioReader has gone stale with its last commit dating back to 2022 and relies on deprecated legacy UI components. Readium2 (`org.readium.kotlin-toolkit`) is actively backed by the Readium Foundation, receives regular security and feature updates, and aligns with modern Android standards.

## 3. Readium2 over Custom WebView Parser
- **Context**: Alternatively, EPUB files (which are zipped HTML/CSS archives) could be unzipped and rendered via custom native `WebView` parsers.
- **Decision**: Standardized on Readium2 Navigator and Streamer components.
- **Rationale**: EPUB 2 and EPUB 3 specifications contain massive complexity (CSS column pagination, non-linear spine items, encrypted LCP DRM, complex CFI location calculation, and JavaScript execution). Writing a custom WebView parser would introduce severe edge-case rendering bugs and security vulnerabilities. Readium2 provides a robust, standardized engine with Compose-native composables and built-in theme/appearance management.

## 4. KSP over KAPT
- **Context**: Compile-time annotation processing is required for Hilt (DI) and Room (DB).
- **Decision**: Used Kotlin Symbol Processing (`com.google.devtools.ksp`) exclusively instead of Kotlin Annotation Processing Tool (`kapt`).
- **Rationale**: `kapt` works by generating Java stubs from Kotlin code before running Java annotation processors, which creates a significant build-time bottleneck. KSP parses Kotlin AST directly, resulting in up to 2x faster compilation speeds and first-class support for Kotlin 2.x compiler features.

## 5. Room FTS4 over LIKE Queries for Search
- **Context**: Implementing fast in-book full-text search across extracted document text.
- **Decision**: Use Room Virtual Tables backed by SQLite FTS4 (`@Fts4`).
- **Rationale**: Standard SQL `LIKE '%query%'` queries require full table scans of raw text strings, causing severe UI jank and high memory consumption on mobile devices when searching large 500+ page books. SQLite FTS4 builds an inverted index word token map, enabling near-instantaneous search results, prefix matching, and excerpt generation. FTS4 was selected over FTS5 to guarantee seamless compatibility across all devices running Android API 26 (Min SDK) without requiring bundled NDK SQLite libraries.

## 6. Dictionary API Choice (`dictionaryapi.dev`)
- **Context**: Enabling in-book word definition popups without bloating app binary size.
- **Decision**: Adopted `https://api.dictionaryapi.dev/api/v2/entries/en/{word}` backed by a 7-day local Room SQLite cache (`DictionaryCache`).
- **Rationale**: Completely free, requires no authentication API keys, has no personal rate limits, and provides structured JSON with phonetic pronunciation and multi-part-of-speech definitions.

## 7. PDF Highlights via Bounding Rect Overlays
- **Context**: Renders user annotations across PDF viewing screens.
- **Decision**: Position-based rectangular coordinate saving drawn via Canvas overlay.
- **Rationale**: The mhiew PDFView fork renders PDF pages directly to Android Display Bitmaps via native NDK PDFium bindings. It does not expose underlying character layout boxes or text selection handles. Bounding rectangle saving ensures users can highlight key paragraphs accurately.

## 8. Stats Activity Chart via Compose Canvas
- **Context**: Displaying weekly reading time metrics in a 7-bar chart.
- **Decision**: Custom Compose `Canvas` drawing instead of adding third-party chart libraries (e.g. Vico, MPAndroidChart).
- **Rationale**: Enforces strict constraint compliance, keeping APK binary footprint minimal and avoiding Gradle dependency conflicts. Taller rounded bars scale dynamically to peak daily reading minutes.
