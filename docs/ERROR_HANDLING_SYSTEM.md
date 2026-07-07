# Error Handling & Reporting System

## How it works

### User experience (what they see)

When ANY error occurs — import failure, corrupt file, parsing crash, missing file — the user gets:

1. **A dialog pops up** with the error message in plain language
2. **"What were you doing?"** text field where they can describe what they were doing
3. **"Send report"** button → sends the report to your Supabase backend
4. **"Report sent ✓"** confirmation → "Thanks! We'll look into this and fix it."
5. **"Dismiss"** button → closes without sending (no pressure)

**No more "Try again later" or cryptic error messages.** Every error is actionable.

### What gets sent in a report

| Field | Example |
|-------|---------|
| `id` | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |
| `timestamp` | `2026-07-04T15:30:45.123Z` |
| `app_version` | `1.0.0` |
| `app_version_code` | `1` |
| `android_version` | `34` (Android 14) |
| `device` | `Infinix SMART 8 Plus` |
| `error_type` | `library_error` or `reader_error` |
| `error_message` | `Unable to import file: Not a valid EPUB: missing META-INF/container.xml` |
| `stack_trace` | Full stack trace (truncated to 4KB) |
| `context` | `Library — importing or loading books` |
| `file_format` | `EPUB` |
| `user_note` | `"I was trying to import a book I downloaded from libgen"` |

### Where it goes

Reports POST to your **Supabase** table via REST API using the OkHttp client that's already in the app dependencies. No new libraries needed.

### How to set up Supabase

1. Go to [supabase.com](https://supabase.com) → create a project
2. Create a table called `error_reports` with these columns:

```sql
CREATE TABLE error_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMPTZ DEFAULT now(),
    app_version TEXT,
    app_version_code INTEGER,
    android_version INTEGER,
    device TEXT,
    error_type TEXT,
    error_message TEXT,
    stack_trace TEXT,
    context TEXT,
    file_format TEXT,
    user_note TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable Row Level Security
ALTER TABLE error_reports ENABLE ROW LEVEL SECURITY;

-- Allow anonymous inserts (from the app)
CREATE POLICY "Allow anonymous insert" ON error_reports
    FOR INSERT TO anon WITH CHECK (true);

-- Only you can read (via dashboard or service role)
-- No SELECT policy for anon = users can't read other reports
```

3. Get your project URL + anon key from Settings → API
4. Paste them into `ErrorReport.kt`:

```kotlin
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJ..."
```

5. Done. Reports flow in.

### Email notifications

In Supabase, set up a **Database Webhook** or **Edge Function** that triggers on INSERT to `error_reports` and sends you an email. Or use Supabase's built-in Slack/Discord webhook integration.

### Error paths covered

| Scenario | What happens |
|----------|-------------|
| Import a corrupt file | Dialog: "Unable to import file: [reason]" + Send report |
| Import a file >500MB | Dialog: "File too large (>500 MB)" + Send report |
| Open a deleted file | Reader shows "File not found" + Back button |
| EPUB missing structure | Dialog: "Unable to read EPUB: missing META-INF/container.xml" + Send report |
| MOBI with DRM | Reader shows: "May be DRM-protected, try converting with Calibre" |
| Parser OOM on huge file | Dialog: "File is too large to display in memory" + Send report |
| Any unknown exception | Dialog with message + stack trace sent silently |
| Comic page can't load | Shows "Unable to load page" text in the viewer |
| Library database error | Dialog: "Unable to load library" + Send report |

### Safety guarantees

- **Reporting never crashes the app** — wrapped in try/catch, fails silently
- **10-second timeout** on network calls
- **No data sent without user action** — dialog must be explicitly dismissed or submitted
- **Stack traces truncated to 4KB** — prevents huge payloads
- **Manual JSON serialization** — no Moshi adapter overhead, no reflection
- **Works offline** — if no connection, report silently fails, error still shown to user

## Files

**New:**
- `data/error/ErrorReport.kt` — `CrashReport` data class, `ErrorReporter` singleton (Supabase POST via OkHttp)
- `presentation/components/ErrorReportDialog.kt` — Material 3 dialog with error message, user note field, send/dismiss

**Modified:**
- `LibraryViewModel.kt` — `ErrorReporter` injected, `showErrorReport`/`lastErrorThrowable` state, `SendErrorReport`/`DismissErrorReport` events, import errors trigger dialog
- `LibraryScreen.kt` — `ErrorReportDialog` wired into UI
- `ReaderViewModel.kt` — `ErrorReporter` injected, same error report pattern, all 4 content loading errors trigger dialog
- `ReaderScreen.kt` — `ErrorReportDialog` wired into UI
