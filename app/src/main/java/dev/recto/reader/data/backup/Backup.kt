package dev.recto.reader.data.backup

import android.content.Context
import android.net.Uri
import dev.recto.reader.data.db.AnnotationEntity
import dev.recto.reader.data.db.RectoDatabase
import dev.recto.reader.data.db.VocabularyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup and restore for everything the reader cannot get back.
 *
 * WHAT IS IN IT, AND WHAT IS NOT
 * Highlights, notes, bookmarks, saved vocabulary with its SM-2 schedule,
 * reading positions and reading history. Not the books themselves - a
 * library of EPUBs is hundreds of megabytes, they came from somewhere, and
 * a backup you cannot email is a backup nobody makes. This file is a few
 * hundred kilobytes and holds the part that is genuinely irreplaceable: the
 * years of margin notes, not the books they are written in.
 *
 * HOW ANNOTATIONS FIND THEIR BOOK AGAIN
 * Row ids are useless across installs - the new database will number things
 * differently. So each book is exported with its identity (content hash,
 * then normalised title/author) and its annotations are nested underneath.
 * Restore matches on those, in that order, and skips books it cannot find
 * rather than dropping their notes into the void.
 *
 * That means the useful order is: reinstall, re-add your books, THEN
 * restore. Restoring first leaves the notes for missing books unmatched -
 * which is why they are reported rather than silently discarded.
 *
 * FORMAT
 * Plain JSON, one object, [VERSION] at the top. Not the SQLite file: a
 * database copy would break the moment the schema moved, cannot be merged
 * into an existing library, and is unreadable if this app ever disappears.
 * JSON can be opened in any text editor in ten years, which is the actual
 * promise being made here.
 */
object Backup {

    /**
     * Bumped only for changes a reader cannot cope with. New optional fields
     * do not need it - [restore] treats every field as optional and uses
     * defaults, so an older file restores into a newer app unchanged.
     */
    const val VERSION = 1

    const val MIME = "application/json"

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        // Sortable and filesystem-safe without pulling in date formatting.
        val days = now / 86_400_000L
        return "recto-backup-$days.json"
    }

    // --- export ---

    suspend fun export(context: Context, target: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val db = RectoDatabase.get(context)
                val books = db.bookDao().allOnce()
                val annotations = db.annotationDao().allOnce()
                val vocabulary = db.lookupDao().allVocabularyOnce()
                val sessions = db.readingSessionDao().allOnce()

                val byBook = annotations.groupBy { it.bookId }

                val booksJson = JSONArray()
                books.forEach { book ->
                    val notes = byBook[book.id].orEmpty()
                    // A book with no annotations and no progress carries no
                    // information worth restoring - it is just a filename.
                    if (notes.isEmpty() && book.progress <= 0f && book.locator == null) {
                        return@forEach
                    }
                    booksJson.put(
                        JSONObject().apply {
                            put("title", book.title)
                            putOpt("author", book.author)
                            putOpt("contentHash", book.contentHash)
                            putOpt("matchTitle", book.matchTitle)
                            putOpt("matchAuthor", book.matchAuthor)
                            put("progress", book.progress.toDouble())
                            putOpt("locator", book.locator)
                            put("isFinished", book.isFinished)
                            put("annotations", annotationsToJson(notes))
                        }
                    )
                }

                val vocabJson = JSONArray()
                vocabulary.forEach { v ->
                    vocabJson.put(
                        JSONObject().apply {
                            put("word", v.word)
                            put("definition", v.definition)
                            putOpt("partOfSpeech", v.partOfSpeech)
                            putOpt("phonetic", v.phonetic)
                            putOpt("contextSentence", v.contextSentence)
                            putOpt("bookTitle", v.bookTitle)
                            put("addedAt", v.addedAt)
                            put("easeFactor", v.easeFactor.toDouble())
                            put("intervalDays", v.intervalDays)
                            put("repetitions", v.repetitions)
                            put("dueAt", v.dueAt)
                            putOpt("lastReviewedAt", v.lastReviewedAt)
                            put("timesReviewed", v.timesReviewed)
                            put("timesCorrect", v.timesCorrect)
                        }
                    )
                }

                val sessionsJson = JSONArray()
                sessions.forEach { s ->
                    sessionsJson.put(
                        JSONObject().apply {
                            put("bookTitle", s.bookTitle)
                            put("startedAt", s.startedAt)
                            put("endedAt", s.endedAt)
                            put("dayKey", s.dayKey)
                            put("millisRead", s.millisRead)
                            put("pagesTurned", s.pagesTurned)
                            put("charsRead", s.charsRead)
                        }
                    )
                }

                val root = JSONObject().apply {
                    put("version", VERSION)
                    put("exportedAt", System.currentTimeMillis())
                    put("app", "Recto")
                    put("books", booksJson)
                    put("vocabulary", vocabJson)
                    put("sessions", sessionsJson)
                }

                // Streamed as text; the whole document is already in memory
                // as a string, and at this size that is fine - a heavy
                // reader's annotations are well under a megabyte.
                context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the file for writing")

                BackupResult.Exported(
                    books = booksJson.length(),
                    annotations = annotations.size,
                    words = vocabulary.size
                )
            }.getOrElse { BackupResult.Failed(it.message ?: "Backup failed") }
        }

    private fun annotationsToJson(notes: List<AnnotationEntity>): JSONArray {
        val arr = JSONArray()
        notes.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("kind", a.kind)
                    put("startChar", a.startChar)
                    put("endChar", a.endChar)
                    put("selectedText", a.selectedText)
                    putOpt("note", a.note)
                    put("colour", a.colour)
                    put("chapterIndex", a.chapterIndex)
                    putOpt("chapterTitle", a.chapterTitle)
                    put("createdAt", a.createdAt)
                    put("updatedAt", a.updatedAt)
                }
            )
        }
        return arr
    }

    // --- restore ---

    /**
     * Merges a backup into the current library.
     *
     * Merge, not replace. Restoring must never be the thing that loses work,
     * so nothing is deleted: annotations already present are left alone and
     * duplicates are skipped by matching span and kind, which makes running
     * a restore twice harmless.
     */
    suspend fun restore(context: Context, source: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(source)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Could not open the file")

                val root = JSONObject(text)
                val version = root.optInt("version", 1)
                if (version > VERSION) {
                    return@withContext BackupResult.Failed(
                        "This backup was made by a newer version of Recto " +
                            "(format $version). Update the app and try again."
                    )
                }

                val db = RectoDatabase.get(context)
                val bookDao = db.bookDao()
                val annotationDao = db.annotationDao()
                val lookupDao = db.lookupDao()

                val library = bookDao.allOnce()

                var restoredNotes = 0
                var skippedNotes = 0
                val missingBooks = mutableListOf<String>()

                val books = root.optJSONArray("books") ?: JSONArray()
                for (i in 0 until books.length()) {
                    val b = books.optJSONObject(i) ?: continue
                    val title = b.optString("title").ifBlank { continue }

                    val hash = b.optStringOrNull("contentHash")
                    val mTitle = b.optStringOrNull("matchTitle")
                    val mAuthor = b.optStringOrNull("matchAuthor")

                    val match = library.firstOrNull { it.contentHash != null && it.contentHash == hash }
                        ?: library.firstOrNull {
                            mTitle != null && it.matchTitle == mTitle &&
                                (mAuthor == null || it.matchAuthor == mAuthor)
                        }

                    val notes = b.optJSONArray("annotations") ?: JSONArray()

                    if (match == null) {
                        missingBooks += title
                        skippedNotes += notes.length()
                        continue
                    }

                    // Read the existing spans ONCE per book rather than
                    // querying inside the loop; a book with 500 highlights
                    // would otherwise mean 500 round trips.
                    val existing = annotationDao.forBookOnce(match.id)
                        .map { Triple(it.kind, it.startChar, it.endChar) }
                        .toHashSet()

                    for (j in 0 until notes.length()) {
                        val n = notes.optJSONObject(j) ?: continue
                        val kind = n.optString("kind").ifBlank { continue }
                        val start = n.optInt("startChar", -1)
                        val end = n.optInt("endChar", -1)
                        if (start < 0 || end < start) continue

                        if (!existing.add(Triple(kind, start, end))) {
                            skippedNotes++
                            continue
                        }

                        annotationDao.insert(
                            AnnotationEntity(
                                bookId = match.id,
                                kind = kind,
                                startChar = start,
                                endChar = end,
                                selectedText = n.optString("selectedText"),
                                note = n.optStringOrNull("note"),
                                colour = n.optInt("colour", 0),
                                chapterIndex = n.optInt("chapterIndex", 0),
                                chapterTitle = n.optStringOrNull("chapterTitle"),
                                createdAt = n.optLong(
                                    "createdAt",
                                    System.currentTimeMillis()
                                ),
                                updatedAt = n.optLong(
                                    "updatedAt",
                                    System.currentTimeMillis()
                                )
                            )
                        )
                        restoredNotes++
                    }

                    // Only move the reading position forward. If this phone
                    // is further into the book than the backup, the backup is
                    // the stale one and must not drag you backwards.
                    val progress = b.optDouble("progress", 0.0).toFloat()
                    val locator = b.optStringOrNull("locator")
                    if (locator != null && progress > match.progress) {
                        bookDao.saveProgress(
                            id = match.id,
                            progress = progress.coerceIn(0f, 1f),
                            locator = locator,
                            at = match.lastOpenedAt ?: System.currentTimeMillis()
                        )
                    }
                }

                var restoredWords = 0
                val vocab = root.optJSONArray("vocabulary") ?: JSONArray()
                for (i in 0 until vocab.length()) {
                    val v = vocab.optJSONObject(i) ?: continue
                    val word = v.optString("word").ifBlank { continue }
                    // IGNORE on conflict, so a word already saved keeps the
                    // schedule it has on this device rather than being reset
                    // to whatever the backup remembers.
                    val id = lookupDao.save(
                        VocabularyEntity(
                            word = word,
                            definition = v.optString("definition"),
                            partOfSpeech = v.optStringOrNull("partOfSpeech"),
                            phonetic = v.optStringOrNull("phonetic"),
                            contextSentence = v.optStringOrNull("contextSentence"),
                            bookTitle = v.optStringOrNull("bookTitle"),
                            addedAt = v.optLong("addedAt", System.currentTimeMillis()),
                            easeFactor = v.optDouble("easeFactor", 2.5).toFloat(),
                            intervalDays = v.optInt("intervalDays", 0),
                            repetitions = v.optInt("repetitions", 0),
                            dueAt = v.optLong("dueAt", System.currentTimeMillis()),
                            lastReviewedAt = if (v.isNull("lastReviewedAt")) {
                                null
                            } else {
                                v.optLong("lastReviewedAt")
                            },
                            timesReviewed = v.optInt("timesReviewed", 0),
                            timesCorrect = v.optInt("timesCorrect", 0)
                        )
                    )
                    if (id != -1L) restoredWords++
                }

                BackupResult.Restored(
                    annotations = restoredNotes,
                    words = restoredWords,
                    skipped = skippedNotes,
                    missingBooks = missingBooks.distinct()
                )
            }.getOrElse {
                BackupResult.Failed(
                    it.message?.takeIf { m -> m.isNotBlank() }
                        ?: "That file could not be read as a Recto backup"
                )
            }
        }
}

/** JSONObject.optString returns "" for a missing key, which is rarely wanted. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

sealed interface BackupResult {
    data class Exported(val books: Int, val annotations: Int, val words: Int) : BackupResult

    data class Restored(
        val annotations: Int,
        val words: Int,
        val skipped: Int,
        val missingBooks: List<String>
    ) : BackupResult

    data class Failed(val message: String) : BackupResult
}
