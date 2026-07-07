# Launch Checklist — All Items Built

## ✅ Completed Features

### Launch Phase 1 — Text-to-Speech (TTS)
- `TtsController` singleton using Android system `TextToSpeech`
- Play/pause/stop/skip forward/skip backward controls
- Smart text chunking at sentence boundaries (~500 chars per chunk)
- Adjustable speed (0.5x – 3.0x)
- Floating TTS control bar at bottom of reader (⏹ ⏮ ▶/⏸ ⏭ + progress)
- 🔊 button in reader top controls for text-based files
- Auto-stops on ViewModel clear (leaving reader)
- Uses system TTS engine — no internet, no new dependencies

### Launch Phase 2 — Bookmarks
- `Bookmark` Room entity with foreign key to `Book`
- DAO methods: getBookmarks, insertBookmark, deleteBookmark
- Database bumped to version 2
- Bookmark toggle button (🔖/🏷️) in reader top controls — works for ALL formats
- Current page auto-detects if bookmarked
- Bookmarks persist across sessions
- Haptic feedback on bookmark toggle

### Launch Phase 3 — Reading Stats
- Tracked in DataStore (no database migration needed):
  - **Total reading time** (minutes, accumulated per session)
  - **Books opened** (increment on each book open)
  - **Current streak** (consecutive days, auto-calculates, resets on gap)
  - **Last read date** (for streak calculation)
- Session duration recorded on ViewModel `onCleared`
- READING STATS section in Settings:
  - "Total reading time: 2h 45m"
  - "Books opened: 14 books"
  - "Current streak: 5 days 🔥"
- Smart formatting: minutes → hours → days

### Launch Phase 4 — Error Handling + Reporting (from previous)
- Already built: ErrorReporter → Supabase, ErrorReportDialog, all error paths covered

### Launch Phase 5 — Security Hardening (from previous)
- Already built: network_security_config, data_extraction_rules, ProGuard, file limits

### Launch Phase 6 — About Screen (from previous)
- Already built: formats list, security statement, architecture declaration

## 📋 Updated Status

| Item | Status |
|------|--------|
| TTS integration | ✅ |
| Bookmarks | ✅ |
| Reading stats | ✅ |
| Error reporting (Supabase) | ✅ |
| Security hardening | ✅ |
| About screen | ✅ |
| ProGuard rules | ✅ |
| 30 file formats | ✅ |
| Search in document | ✅ |
| Font scaling | ✅ |
| 3 themes | ✅ |
| Library management | ✅ |
| Comic reader | ✅ |
| Haptic feedback | ✅ |
| Animations/polish | ✅ |
| Performance optimised | ✅ |
| Owner flag | 🔧 Set in Supabase URL (configured = owner) |

## 🚀 Ready to launch on Play Store

Remaining manual tasks:
- [ ] Set Supabase URL + key in ErrorReport.kt
- [ ] Create Play Store listing (screenshots, description)
- [ ] Create privacy policy page
- [ ] Generate signed APK/AAB for release
- [ ] Set versionCode > 1 for release
