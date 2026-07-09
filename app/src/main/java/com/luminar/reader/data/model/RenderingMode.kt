// app/src/main/java/com/luminar/reader/data/model/RenderingMode.kt
package com.luminar.reader.data.model

enum class RenderingMode {
    DOCUMENT,       // Word-like: paper cards, serif font, margins
    SPREADSHEET,    // Excel-like: grid, horizontal scroll, row numbers
    PRESENTATION,   // PowerPoint-like: slide cards
    EBOOK,          // Kindle-like: clean reading, chapter nav
    CODE,           // VS Code-like: line numbers, monospace, syntax colors
    MARKDOWN,       // Obsidian-like: rendered markdown
    COMIC,          // Comic reader: image pages
    PLAIN_TEXT,     // Simple text rendering
    PDF             // Native PDF viewer
}
