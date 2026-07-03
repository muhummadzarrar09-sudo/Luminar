// app/src/main/java/com/luminar/reader/data/model/ScrollMode.kt
package com.luminar.reader.data.model

enum class ScrollMode(val displayName: String, val icon: String) {
    VERTICAL_SCROLL("Scroll", "↕"),
    PAGED("Pages", "⊞");

    fun next(): ScrollMode = when (this) {
        VERTICAL_SCROLL -> PAGED
        PAGED -> VERTICAL_SCROLL
    }
}

enum class FontScale(val displayName: String, val multiplier: Float) {
    TINY("Tiny", 0.75f),
    SMALL("Small", 0.88f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.15f),
    HUGE("Huge", 1.35f),
    MASSIVE("Massive", 1.6f);

    fun next(): FontScale {
        val values = entries
        val nextIndex = (values.indexOf(this) + 1) % values.size
        return values[nextIndex]
    }

    fun previous(): FontScale {
        val values = entries
        val prevIndex = (values.indexOf(this) - 1 + values.size) % values.size
        return values[prevIndex]
    }
}
