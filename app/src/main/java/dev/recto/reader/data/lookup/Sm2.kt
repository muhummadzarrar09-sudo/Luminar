package dev.recto.reader.data.lookup

import dev.recto.reader.data.db.VocabularyEntity
import kotlin.math.roundToInt

/**
 * How well you recalled a card. SM-2 uses a 0-5 scale; four buttons is the
 * most a phone UI can present without the choice itself becoming work, so
 * these map onto the useful part of the range.
 */
enum class Recall(val label: String, val quality: Int) {
    AGAIN("Again", 1),
    HARD("Hard", 3),
    GOOD("Good", 4),
    EASY("Easy", 5)
}

/**
 * SM-2, the SuperMemo scheduling algorithm Anki is built on.
 *
 * The idea: review a card just before you would have forgotten it. Each
 * correct answer pushes the next review further out; a lapse pulls it right
 * back to tomorrow. Cards you find hard come back often, cards you know
 * drift toward months apart, so study time goes where it is needed.
 */
object Sm2 {

    private const val MIN_EASE = 1.3f
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Result(
        val easeFactor: Float,
        val intervalDays: Int,
        val repetitions: Int,
        val dueAt: Long
    )

    fun schedule(
        card: VocabularyEntity,
        recall: Recall,
        now: Long = System.currentTimeMillis()
    ): Result {
        val q = recall.quality

        // Below 3 is a lapse: start the ladder again. The ease factor is NOT
        // reset, so a word you have struggled with before stays scheduled
        // tightly rather than pretending to be new.
        if (q < 3) {
            return Result(
                easeFactor = maxOf(MIN_EASE, card.easeFactor - 0.20f),
                intervalDays = 0,
                repetitions = 0,
                // Ten minutes, not tomorrow. A word you just failed should
                // come back inside the same session.
                dueAt = now + 10 * 60 * 1000
            )
        }

        val reps = card.repetitions + 1

        val interval = when (reps) {
            1 -> 1
            2 -> 6
            else -> (card.intervalDays * card.easeFactor).roundToInt().coerceAtLeast(1)
        }

        // The standard SM-2 ease adjustment. "Good" (q=4) leaves ease
        // unchanged; "Easy" nudges it up, "Hard" down.
        val ease = (
            card.easeFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
            ).coerceAtLeast(MIN_EASE)

        // Cap at roughly a year. Beyond that the scheduling is noise, and a
        // card you have not seen in two years may as well be new.
        val capped = interval.coerceAtMost(365)

        return Result(
            easeFactor = ease,
            intervalDays = capped,
            repetitions = reps,
            dueAt = now + capped * DAY_MS
        )
    }

    /** Human-readable "next review" for the buttons, e.g. "6d". */
    fun previewInterval(card: VocabularyEntity, recall: Recall): String {
        val result = schedule(card, recall)
        return when {
            result.intervalDays == 0 -> "10m"
            result.intervalDays == 1 -> "1d"
            result.intervalDays < 30 -> "${result.intervalDays}d"
            result.intervalDays < 365 -> "${result.intervalDays / 30}mo"
            else -> "1y"
        }
    }
}
