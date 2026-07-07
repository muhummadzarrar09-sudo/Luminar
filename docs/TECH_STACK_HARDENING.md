# Tech Stack Hardening — Security & Production Readiness

## Security Fixes

### 1. 🔒 Network Security Config
**Before:** `android:usesCleartextTraffic="true"` — allowed HTTP everywhere. Any network call could be intercepted.

**After:**
- `android:usesCleartextTraffic="false"` globally
- `network_security_config.xml` restricts cleartext to **localhost/LAN only** (for Ollama AI server)
- All other connections require HTTPS
- System CA certificates trusted

### 2. 🛡️ Data Extraction Rules (Android 12+)
New `data_extraction_rules.xml`:
- **Cloud backup excluded:** Database + internal book files NOT backed up to Google Drive (prevents data leaks)
- **DataStore preferences allowed:** Theme/font prefs can sync
- **Device transfer allowed:** All files transfer during device migration

### 3. 📦 ProGuard / R8 Rules
New `proguard-rules.pro`:
- Keeps Room entities, DAOs, Hilt classes intact
- Keeps Moshi/Retrofit models for API communication
- Obfuscates everything else — decompiling gives gibberish
- Preserves line numbers for crash reports
- Suppresses warnings for optional OkHttp platform deps

### 4. 📏 File Size Limits
**Import guard:** 500 MB hard limit on file imports. Reads in 8KB chunks and tracks total bytes — kills the import immediately if exceeded. Prevents OOM on huge files.

**Parser guard:** Text-based files >50 MB get a warning message instead of being loaded into memory. ZIP-based formats (DOCX, EPUB, etc.) exempt since they stream from the archive.

### 5. 🧱 Parser Error Boundaries
`DocumentParser.parseToText()` now wraps ALL parsing in a try/catch:
- `OutOfMemoryError` → friendly "file too large" message
- Any `Exception` → formatted error with filename and format type
- No crash, no ANR — worst case the user sees an error message

### 6. 📖 EPUB Validation
Before parsing, EPUBs are validated:
- File must exist
- Minimum size 100 bytes (catch corrupt/empty files)
- Maximum size 500 MB
- Missing `container.xml` → clear error

### 7. 🔐 Manifest Hardening
- `android:allowBackup="false"` — prevents adb backup data theft
- `android:dataExtractionRules` — Android 12+ backup control
- `android:networkSecurityConfig` — enforced HTTPS
- `android:usesCleartextTraffic="false"` — double protection
- INTERNET permission with clear purpose (Ollama AI only)

## Production Readiness

### About Section (Settings)
Expanded with:
- Supported formats list (all 30)
- Security statement: "All files stored locally · No data collection · No analytics · HTTPS enforced"
- Architecture declaration: "Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · Zero external parser dependencies"

### Room Migration Safety
`fallbackToDestructiveMigration()` retained with documentation comment. For v2.0+ production, each schema change should use a proper `Migration` object to preserve user data.

### What's NOT in this app (by design)
- ❌ No analytics / tracking / telemetry
- ❌ No ads, ad SDKs, or ad IDs
- ❌ No user accounts or registration
- ❌ No data sent to any server (except optional Ollama on LAN)
- ❌ No Firebase, no Google Services dependencies
- ❌ No third-party crash reporting
- ❌ No clipboard reading
- ❌ No camera/mic/contacts/location permissions

This is a **fully local, offline-first reader**. The only network permission exists for the future Ollama AI feature, and it's locked to localhost/LAN via network security config.

## Files Changed

**New files:**
- `res/xml/network_security_config.xml`
- `res/xml/data_extraction_rules.xml`
- `proguard-rules.pro`

**Modified:**
- `AndroidManifest.xml` — network config, data extraction rules, cleartext disabled
- `BookRepositoryImpl.kt` — 500MB file size limit with chunked copy
- `DocumentParser.kt` — OOM catch, 50MB text guard, try/catch wrapper
- `EpubParser.kt` — file validation (exists, size bounds)
- `AppModule.kt` — documented migration comment
- `SettingsScreen.kt` — expanded About section
- `strings.xml` — app description
