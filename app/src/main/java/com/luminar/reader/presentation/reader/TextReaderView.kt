// app/src/main/java/com/luminar/reader/presentation/reader/TextReaderView.kt
package com.luminar.reader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminar.reader.data.model.AppTheme
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.FontScale
import com.luminar.reader.data.model.RenderingMode
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.readerBackgroundColor
import com.luminar.reader.presentation.theme.readerControlsContentColor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
@Composable
fun TextReaderView(
    content: String,
    format: BookFormat,
    renderingMode: RenderingMode = format.renderingMode,
    theme: AppTheme,
    fontScale: FontScale,
    searchQuery: String,
    isSearchActive: Boolean,
    currentMatchBlockIndex: Int?,
    scrollToBlockIndex: Int?,
    onToggleControls: () -> Unit,
    onScrollPositionChanged: (Int) -> Unit,
    onBlocksParsed: (List<String>) -> Unit,
    onScrollToBlockConsumed: () -> Unit,
    initialScrollPosition: Int
) {
    val isDocumentMode = renderingMode == RenderingMode.DOCUMENT
    val isCodeMode = renderingMode == RenderingMode.CODE
    val backgroundColor = theme.readerBackgroundColor()
    val textColor = theme.readerControlsContentColor()
    val listState = rememberLazyListState()

    val blocks = remember(content, format) {
        if (format == BookFormat.MARKDOWN) {
            parseMarkdownBlocks(content)
        } else {
            parseTextBlocks(content, format)
        }
    }

    // Report block texts to ViewModel for search
    LaunchedEffect(blocks) {
        val texts = blocks.map { block ->
            when (block) {
                is TextBlock.Heading -> block.text
                is TextBlock.Paragraph -> block.spans.text
                is TextBlock.CodeBlock -> block.code
                is TextBlock.BulletItem -> block.spans.text
                is TextBlock.TaskItem -> block.spans.text
                is TextBlock.NumberedItem -> block.spans.text
                is TextBlock.Quote -> block.spans.text
                is TextBlock.PlainLine -> block.text
                is TextBlock.Table -> (block.headers + block.rows.flatten()).joinToString(" ")
                TextBlock.Divider -> ""
            }
        }
        onBlocksParsed(texts)
    }

    // Restore scroll position
    LaunchedEffect(initialScrollPosition, blocks.size) {
        if (initialScrollPosition > 0 && blocks.isNotEmpty()) {
            val index = initialScrollPosition.coerceIn(0, blocks.lastIndex)
            listState.scrollToItem(index)
        }
    }

    // Scroll to search result
    LaunchedEffect(scrollToBlockIndex) {
        if (scrollToBlockIndex != null && blocks.isNotEmpty()) {
            val idx = scrollToBlockIndex.coerceIn(0, blocks.lastIndex)
            listState.animateScrollToItem(idx)
            onScrollToBlockConsumed()
        }
    }

    // Report scroll position changes
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(400)
            .collect { index ->
                onScrollPositionChanged(index)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggleControls() }
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (isDocumentMode) 12.dp else 20.dp,
                    end = if (isDocumentMode) 12.dp else 20.dp,
                    top = if (isSearchActive) 120.dp else 72.dp,
                    bottom = 120.dp
                )
            ) {
                // Code mode: file info header
                if (isCodeMode) {
                    item {
                        CodeFileHeader(
                            lineCount = content.lines().size,
                            charCount = content.length,
                            textColor = textColor
                        )
                    }
                }

                items(blocks.size) { index ->
                    val block = blocks[index]
                    val isThisTheCurrentMatch = isSearchActive &&
                        currentMatchBlockIndex == index

                    RenderBlock(
                        block = block,
                        textColor = textColor,
                        theme = theme,
                        fontScale = fontScale,
                        renderingMode = renderingMode,
                        searchQuery = if (isSearchActive) searchQuery else "",
                        isCurrentMatch = isThisTheCurrentMatch
                    )
                }
            }
        }
    }
}

// ─── Markdown block model ────────────────────────────────────

internal sealed interface TextBlock {
    data class Heading(val level: Int, val text: String) : TextBlock
    data class Paragraph(val spans: AnnotatedString) : TextBlock
    data class CodeBlock(val code: String, val language: String?) : TextBlock
    data class BulletItem(val spans: AnnotatedString, val indent: Int = 0) : TextBlock
    data class TaskItem(val spans: AnnotatedString, val checked: Boolean) : TextBlock
    data class NumberedItem(val number: String, val spans: AnnotatedString) : TextBlock
    data class Quote(val spans: AnnotatedString) : TextBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : TextBlock
    data object Divider : TextBlock
    data class PlainLine(val text: String) : TextBlock
}

// ─── Rendering ───────────────────────────────────────────────

@Composable
private fun RenderBlock(
    block: TextBlock,
    textColor: Color,
    theme: AppTheme,
    fontScale: FontScale,
    renderingMode: RenderingMode = RenderingMode.MARKDOWN,
    searchQuery: String,
    isCurrentMatch: Boolean
) {
    val scale = fontScale.multiplier
    val highlightColor = LuminarGold.copy(alpha = 0.3f)
    val currentHighlightColor = LuminarGold.copy(alpha = 0.65f)
    val isDoc = renderingMode == RenderingMode.DOCUMENT
    val isCode = renderingMode == RenderingMode.CODE
    val docFont = if (isDoc) FontFamily.Serif else FontFamily.Default

    when (block) {
        is TextBlock.Heading -> {
            val fontSize = when (block.level) {
                1 -> 26.sp * scale
                2 -> 22.sp * scale
                3 -> 19.sp * scale
                4 -> 17.sp * scale
                else -> 16.sp * scale
            }
            Text(
                text = highlightText(block.text, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (block.level <= 2) 20.dp else 14.dp, bottom = 8.dp),
                color = LuminarGold,
                fontFamily = if (isDoc) FontFamily.Serif else FontFamily.Default,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = fontSize * 1.3f
            )
            if (block.level <= 2) {
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = textColor.copy(alpha = 0.15f)
                )
            }
        }

        is TextBlock.Paragraph -> {
            Text(
                text = highlightAnnotatedString(block.spans, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isDoc) 8.dp else 6.dp),
                color = textColor,
                fontFamily = docFont,
                fontSize = (if (isDoc) 16.sp else 15.sp) * scale,
                lineHeight = (if (isDoc) 28.sp else 24.sp) * scale
            )
        }

        is TextBlock.CodeBlock -> {
            val codeBg = when (theme) {
                AppTheme.DARK_AMOLED -> Color(0xFF1A1A1A)
                AppTheme.SEPIA -> Color(0xFFE2D5B5)
                AppTheme.LIGHT -> Color(0xFFEEEEEE)
            }

            if (isCode) {
                // IDE Mode: line numbers + syntax highlighting
                val lines = block.code.lines()
                val gutterWidth = (lines.size.toString().length * 10 + 16).dp
                val gutterColor = textColor.copy(alpha = 0.25f)
                val gutterBg = when (theme) {
                    AppTheme.DARK_AMOLED -> Color(0xFF141414)
                    AppTheme.SEPIA -> Color(0xFFD9CEB2)
                    AppTheme.LIGHT -> Color(0xFFE2E2E2)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(codeBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                ) {
                    lines.forEachIndexed { lineIdx, line ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Line number gutter
                            Box(
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .background(gutterBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "${lineIdx + 1}",
                                    color = gutterColor,
                                    fontSize = 11.sp * scale,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Code line with syntax coloring
                            Text(
                                text = syntaxHighlight(line, block.language, theme),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                fontSize = 13.sp * scale,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp * scale,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                // Non-code mode: simple code block (markdown fenced etc.)
                val codeColor = when (theme) {
                    AppTheme.DARK_AMOLED -> Color(0xFF8BE9FD)
                    AppTheme.SEPIA -> Color(0xFF5E4300)
                    AppTheme.LIGHT -> Color(0xFF383838)
                }
                Text(
                    text = highlightText(block.code, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(codeBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    color = codeColor,
                    fontSize = 13.sp * scale,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp * scale
                )
            }
        }

        is TextBlock.BulletItem -> {
            Text(
                text = buildAnnotatedString {
                    append("  ".repeat(block.indent))
                    append("•  ")
                    append(highlightAnnotatedString(block.spans, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isDoc) 5.dp else 3.dp, horizontal = if (isDoc) 12.dp else 4.dp),
                color = textColor,
                fontFamily = docFont,
                fontSize = (if (isDoc) 16.sp else 15.sp) * scale,
                lineHeight = (if (isDoc) 26.sp else 23.sp) * scale
            )
        }

        is TextBlock.TaskItem -> {
            val checkbox = if (block.checked) "☑" else "☐"
            val checkColor = if (block.checked) LuminarGold else textColor.copy(alpha = 0.4f)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = checkColor, fontSize = 16.sp * scale)) {
                        append("$checkbox  ")
                    }
                    append(highlightAnnotatedString(block.spans, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                color = if (block.checked) textColor.copy(alpha = 0.5f) else textColor,
                fontFamily = docFont,
                fontSize = (if (isDoc) 16.sp else 15.sp) * scale,
                lineHeight = (if (isDoc) 26.sp else 23.sp) * scale
            )
        }

        is TextBlock.NumberedItem -> {
            Text(
                text = buildAnnotatedString {
                    append("${block.number}  ")
                    append(highlightAnnotatedString(block.spans, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                color = textColor,
                fontSize = 15.sp * scale,
                lineHeight = 23.sp * scale
            )
        }

        is TextBlock.Quote -> {
            val quoteBg = when (theme) {
                AppTheme.DARK_AMOLED -> Color(0xFF161616)
                AppTheme.SEPIA -> Color(0xFFE6D9BF)
                AppTheme.LIGHT -> Color(0xFFF2F2F2)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(quoteBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            ) {
                Box(modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                    Text(
                        text = highlightAnnotatedString(block.spans, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch),
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 14.sp * scale,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 22.sp * scale
                    )
                }
            }
        }

        is TextBlock.Table -> {
            val borderColor = textColor.copy(alpha = 0.15f)
            val isSpreadsheet = renderingMode == RenderingMode.SPREADSHEET
            val headerBg = if (isSpreadsheet) {
                when (theme) {
                    AppTheme.DARK_AMOLED -> Color(0xFF1E3A1E)
                    AppTheme.SEPIA -> Color(0xFFE6D5A8)
                    AppTheme.LIGHT -> Color(0xFFE2EFDA)
                }
            } else LuminarGold.copy(alpha = 0.1f)
            val headerTextColor = if (isSpreadsheet) {
                when (theme) {
                    AppTheme.DARK_AMOLED -> Color(0xFF90D490)
                    AppTheme.SEPIA -> Color(0xFF4A3B00)
                    AppTheme.LIGHT -> Color(0xFF1F5C2E)
                }
            } else LuminarGold

            // Horizontal scroll for wide tables
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            ) {
                // Horizontally scrollable content
                val scrollMod = if (block.headers.size > 4)
                    Modifier.horizontalScroll(scrollState) else Modifier

                Column(modifier = scrollMod) {
                    // Header row (sticky feel — thicker, bolder)
                    Row(
                        modifier = Modifier
                            .let { if (block.headers.size > 4) it.width(IntrinsicSize.Max) else it.fillMaxWidth() }
                            .background(headerBg)
                            .height(IntrinsicSize.Min)
                    ) {
                        // Row number column for spreadsheet mode
                        if (isSpreadsheet) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#",
                                    color = headerTextColor.copy(alpha = 0.5f),
                                    fontSize = 11.sp * scale,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).background(borderColor))
                        }

                        block.headers.forEachIndexed { idx, header ->
                            Text(
                                text = header,
                                modifier = Modifier
                                    .let { if (block.headers.size > 4) it.width(120.dp) else it.weight(1f) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                color = headerTextColor,
                                fontSize = 13.sp * scale,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3
                            )
                            if (idx < block.headers.lastIndex) {
                                Box(modifier = Modifier.width(1.dp).background(borderColor))
                            }
                        }
                    }

                    // Thick header separator
                    HorizontalDivider(
                        thickness = if (isSpreadsheet) 2.dp else 1.dp,
                        color = if (isSpreadsheet) headerTextColor.copy(alpha = 0.3f) else borderColor
                    )

                    // Data rows with zebra striping
                    block.rows.forEachIndexed { rowIdx, row ->
                        val zebraBg = if (isSpreadsheet && rowIdx % 2 == 1) {
                            textColor.copy(alpha = 0.04f)
                        } else Color.Transparent

                        Row(
                            modifier = Modifier
                                .let { if (block.headers.size > 4) it.width(IntrinsicSize.Max) else it.fillMaxWidth() }
                                .background(zebraBg)
                                .height(IntrinsicSize.Min)
                        ) {
                            // Row number
                            if (isSpreadsheet) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIdx + 1}",
                                        color = textColor.copy(alpha = 0.35f),
                                        fontSize = 10.sp * scale
                                    )
                                }
                                Box(modifier = Modifier.width(1.dp).background(borderColor))
                            }

                            row.forEachIndexed { idx, cell ->
                                val isNumeric = cell.toDoubleOrNull() != null
                                Text(
                                    text = cell,
                                    modifier = Modifier
                                        .let { if (block.headers.size > 4) it.width(120.dp) else it.weight(1f) }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    color = textColor,
                                    fontSize = 12.sp * scale,
                                    textAlign = if (isNumeric) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
                                    fontFamily = if (isNumeric) FontFamily.Monospace else FontFamily.Default,
                                    maxLines = 5
                                )
                                if (idx < row.lastIndex) {
                                    Box(modifier = Modifier.width(1.dp).background(borderColor))
                                }
                            }
                        }
                        if (rowIdx < block.rows.lastIndex) {
                            HorizontalDivider(color = borderColor.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }

        TextBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = textColor.copy(alpha = 0.2f)
            )
        }

        is TextBlock.PlainLine -> {
            Text(
                text = highlightText(block.text, searchQuery, highlightColor, currentHighlightColor, isCurrentMatch),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = textColor,
                fontSize = 14.sp * scale,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp * scale
            )
        }
    }
}

// ─── Search highlighting ─────────────────────────────────────

private fun highlightText(
    text: String,
    query: String,
    highlightColor: Color,
    currentHighlightColor: Color,
    isCurrentMatch: Boolean
): AnnotatedString {
    if (query.length < 2) return AnnotatedString(text)

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val bgColor = if (isCurrentMatch) currentHighlightColor else highlightColor

    return buildAnnotatedString {
        var pos = 0
        while (pos < text.length) {
            val matchIdx = lowerText.indexOf(lowerQuery, pos)
            if (matchIdx < 0) {
                append(text.substring(pos))
                break
            }
            append(text.substring(pos, matchIdx))
            withStyle(SpanStyle(background = bgColor)) {
                append(text.substring(matchIdx, matchIdx + query.length))
            }
            pos = matchIdx + query.length
        }
    }
}

private fun highlightAnnotatedString(
    source: AnnotatedString,
    query: String,
    highlightColor: Color,
    currentHighlightColor: Color,
    isCurrentMatch: Boolean
): AnnotatedString {
    if (query.length < 2) return source

    val plainText = source.text
    val lowerText = plainText.lowercase()
    val lowerQuery = query.lowercase()
    val bgColor = if (isCurrentMatch) currentHighlightColor else highlightColor

    return buildAnnotatedString {
        append(source)
        var pos = 0
        while (pos < plainText.length) {
            val matchIdx = lowerText.indexOf(lowerQuery, pos)
            if (matchIdx < 0) break
            addStyle(SpanStyle(background = bgColor), matchIdx, matchIdx + query.length)
            pos = matchIdx + query.length
        }
    }
}

// ─── Document Paper Card (Word-like) ─────────────────────

@Composable
private fun DocumentPaperCard(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val paperColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFF252525)
        AppTheme.SEPIA -> Color(0xFFFAF4E8)
        AppTheme.LIGHT -> Color.White
    }
    val shadowElevation = if (theme == AppTheme.DARK_AMOLED) 2.dp else 4.dp

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = paperColor,
        shadowElevation = shadowElevation,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

// ─── Code File Header (VS Code-like) ────────────────────

@Composable
private fun CodeFileHeader(
    lineCount: Int,
    charCount: Int,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(
                textColor.copy(alpha = 0.06f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$lineCount lines",
            color = textColor.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "${charCount} chars",
            color = textColor.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─── Syntax highlighting (VS Code-like) ──────────────────

private val KEYWORDS = setOf(
    // Common across most languages
    "fun", "val", "var", "class", "object", "interface", "enum", "data",
    "if", "else", "when", "for", "while", "do", "return", "break", "continue",
    "import", "package", "public", "private", "protected", "internal",
    "abstract", "override", "open", "final", "sealed", "companion",
    "suspend", "async", "await", "try", "catch", "finally", "throw",
    "null", "true", "false", "this", "super", "is", "as", "in", "out",
    // Python
    "def", "self", "None", "True", "False", "lambda", "yield", "from", "with",
    "pass", "raise", "except", "assert", "global", "nonlocal", "del",
    // JS/TS
    "function", "const", "let", "export", "default", "new", "typeof",
    "instanceof", "void", "delete", "switch", "case",
    // Java/C
    "static", "void", "int", "float", "double", "long", "boolean", "char",
    "String", "extends", "implements", "throws", "synchronized",
    // Rust
    "fn", "mut", "pub", "use", "mod", "struct", "impl", "trait", "where",
    "match", "loop", "ref", "move", "unsafe", "extern", "crate",
    // Go
    "func", "type", "map", "range", "defer", "go", "chan", "select",
    // General
    "print", "println", "printf", "console", "log", "require", "include"
)

private fun syntaxHighlight(line: String, language: String?, theme: AppTheme): AnnotatedString {
    val keywordColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFFFF79C6)  // pink
        AppTheme.SEPIA -> Color(0xFF8B0000)
        AppTheme.LIGHT -> Color(0xFF0000FF)
    }
    val stringColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFFF1FA8C)  // yellow
        AppTheme.SEPIA -> Color(0xFF006400)
        AppTheme.LIGHT -> Color(0xFF008000)
    }
    val commentColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFF6272A4)  // muted blue
        AppTheme.SEPIA -> Color(0xFF808080)
        AppTheme.LIGHT -> Color(0xFF808080)
    }
    val numberColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFFBD93F9)  // purple
        AppTheme.SEPIA -> Color(0xFF800080)
        AppTheme.LIGHT -> Color(0xFF800080)
    }
    val defaultColor = when (theme) {
        AppTheme.DARK_AMOLED -> Color(0xFFF8F8F2)
        AppTheme.SEPIA -> Color(0xFF5E4300)
        AppTheme.LIGHT -> Color(0xFF383838)
    }

    return buildAnnotatedString {
        val trimmedLine = line

        // Check for full-line comment
        val commentStart = findCommentStart(trimmedLine)
        if (commentStart == 0) {
            withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                append(trimmedLine)
            }
            return@buildAnnotatedString
        }

        var i = 0
        while (i < trimmedLine.length) {
            // Comment from here to end of line
            if (commentStart > 0 && i == commentStart) {
                withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                    append(trimmedLine.substring(i))
                }
                break
            }

            val ch = trimmedLine[i]

            // String literals
            if (ch == '"' || ch == '\'' || ch == '`') {
                val endIdx = trimmedLine.indexOf(ch, i + 1)
                val strEnd = if (endIdx >= 0) endIdx + 1 else trimmedLine.length
                withStyle(SpanStyle(color = stringColor)) {
                    append(trimmedLine.substring(i, strEnd))
                }
                i = strEnd
                continue
            }

            // Numbers
            if (ch.isDigit() && (i == 0 || !trimmedLine[i - 1].isLetterOrDigit())) {
                var numEnd = i
                while (numEnd < trimmedLine.length && (trimmedLine[numEnd].isDigit() || trimmedLine[numEnd] == '.' || trimmedLine[numEnd] == 'f' || trimmedLine[numEnd] == 'L' || trimmedLine[numEnd] == 'x')) numEnd++
                withStyle(SpanStyle(color = numberColor)) {
                    append(trimmedLine.substring(i, numEnd))
                }
                i = numEnd
                continue
            }

            // Words (potential keywords)
            if (ch.isLetter() || ch == '_') {
                var wordEnd = i
                while (wordEnd < trimmedLine.length && (trimmedLine[wordEnd].isLetterOrDigit() || trimmedLine[wordEnd] == '_')) wordEnd++
                val word = trimmedLine.substring(i, wordEnd)
                if (word in KEYWORDS) {
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                        append(word)
                    }
                } else {
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(word)
                    }
                }
                i = wordEnd
                continue
            }

            // Everything else
            withStyle(SpanStyle(color = defaultColor)) {
                append(ch)
            }
            i++
        }
    }
}

private fun findCommentStart(line: String): Int {
    val stripped = line.trimStart()
    val offset = line.length - stripped.length

    // Single-line comments
    if (stripped.startsWith("//")) return offset
    if (stripped.startsWith("#") && !stripped.startsWith("#!")) return offset
    if (stripped.startsWith("--")) return offset
    if (stripped.startsWith(";")) return offset

    // Inline comment (find // not inside a string)
    var inString = false
    var stringChar = ' '
    for (idx in line.indices) {
        val ch = line[idx]
        if (inString) {
            if (ch == stringChar) inString = false
        } else {
            if (ch == '"' || ch == '\'') {
                inString = true
                stringChar = ch
            } else if (ch == '/' && idx + 1 < line.length && line[idx + 1] == '/') {
                return idx
            } else if (ch == '#' && idx + 1 < line.length && line[idx + 1] != '!') {
                // Python-style inline comment
                return idx
            }
        }
    }
    return -1
}

// ─── Cached regex patterns ───────────────────────────────

private val HR_REGEX = Regex("^[-*_]{3,}$")
private val UL_REGEX = Regex("^[-*+]\\s+.*")
private val OL_REGEX = Regex("^\\d+[.)]+\\s+.*")
private val OL_CAPTURE = Regex("^(\\d+[.)]+)\\s+(.*)")

// ─── Markdown parser ─────────────────────────────────────────

private fun parseMarkdownBlocks(source: String): List<TextBlock> {
    val blocks = mutableListOf<TextBlock>()
    val lines = source.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(TextBlock.CodeBlock(codeLines.joinToString("\n"), lang))
                i++
            }
            trimmed.matches(HR_REGEX) -> {
                blocks.add(TextBlock.Divider)
                i++
            }
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val text = trimmed.drop(level).trimStart()
                blocks.add(TextBlock.Heading(level, text))
                i++
            }
            trimmed.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                blocks.add(TextBlock.Quote(parseInlineMarkdown(quoteLines.joinToString(" "))))
            }
            trimmed.matches(UL_REGEX) -> {
                val indent = line.indexOf(trimmed[0]) / 2
                val text = trimmed.drop(2).trimStart()
                // Check for task list: - [ ] or - [x] or - [X]
                if (text.startsWith("[ ] ") || text.startsWith("[] ")) {
                    val taskText = text.removePrefix("[ ] ").removePrefix("[] ")
                    blocks.add(TextBlock.TaskItem(parseInlineMarkdown(taskText), checked = false))
                } else if (text.startsWith("[x] ") || text.startsWith("[X] ")) {
                    val taskText = text.removePrefix("[x] ").removePrefix("[X] ")
                    blocks.add(TextBlock.TaskItem(parseInlineMarkdown(taskText), checked = true))
                } else {
                    blocks.add(TextBlock.BulletItem(parseInlineMarkdown(text), indent))
                }
                i++
            }
            trimmed.matches(OL_REGEX) -> {
                val match = OL_CAPTURE.find(trimmed)
                if (match != null) {
                    blocks.add(TextBlock.NumberedItem(match.groupValues[1], parseInlineMarkdown(match.groupValues[2])))
                }
                i++
            }
            // Markdown table
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val tLine = lines[i].trim()
                    if (!tLine.startsWith("|")) break
                    // Skip separator rows (| --- | --- |)
                    if (tLine.contains("---")) { i++; continue }
                    val cells = tLine.split("|")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) tableRows.add(cells)
                    i++
                }
                if (tableRows.size >= 1) {
                    val headers = tableRows[0]
                    val data = if (tableRows.size > 1) tableRows.subList(1, tableRows.size) else emptyList()
                    blocks.add(TextBlock.Table(headers, data))
                }
            }

            trimmed.isEmpty() -> { i++ }
            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size) {
                    val pLine = lines[i].trim()
                    if (pLine.isEmpty() || pLine.startsWith("#") || pLine.startsWith("```") ||
                        pLine.startsWith(">") || pLine.matches(UL_REGEX) ||
                        pLine.matches(OL_REGEX) || pLine.matches(HR_REGEX)
                    ) break
                    paraLines.add(pLine)
                    i++
                }
                blocks.add(TextBlock.Paragraph(parseInlineMarkdown(paraLines.joinToString(" "))))
            }
        }
    }

    return blocks
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var pos = 0
        val src = text
        while (pos < src.length) {
            when {
                src.startsWith("***", pos) -> {
                    val end = src.indexOf("***", pos + 3)
                    if (end > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(src.substring(pos + 3, end)) }; pos = end + 3 }
                    else { append(src[pos]); pos++ }
                }
                src.startsWith("**", pos) -> {
                    val end = src.indexOf("**", pos + 2)
                    if (end > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(pos + 2, end)) }; pos = end + 2 }
                    else { append(src[pos]); pos++ }
                }
                (src[pos] == '*' || src[pos] == '_') && pos + 1 < src.length && src[pos + 1] != ' ' -> {
                    val marker = src[pos]; val end = src.indexOf(marker, pos + 1)
                    if (end > 0 && src[end - 1] != ' ') { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(pos + 1, end)) }; pos = end + 1 }
                    else { append(src[pos]); pos++ }
                }
                src.startsWith("~~", pos) -> {
                    val end = src.indexOf("~~", pos + 2)
                    if (end > 0) { withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(src.substring(pos + 2, end)) }; pos = end + 2 }
                    else { append(src[pos]); pos++ }
                }
                src[pos] == '`' -> {
                    val end = src.indexOf('`', pos + 1)
                    if (end > 0) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = Color(0x22888888))) { append(" ${src.substring(pos + 1, end)} ") }; pos = end + 1 }
                    else { append(src[pos]); pos++ }
                }
                src[pos] == '[' -> {
                    val cb = src.indexOf(']', pos + 1)
                    if (cb > 0 && cb + 1 < src.length && src[cb + 1] == '(') {
                        val cp = src.indexOf(')', cb + 2)
                        if (cp > 0) { withStyle(SpanStyle(color = LuminarGold, textDecoration = TextDecoration.Underline)) { append(src.substring(pos + 1, cb)) }; pos = cp + 1 }
                        else { append(src[pos]); pos++ }
                    } else { append(src[pos]); pos++ }
                }
                else -> { append(src[pos]); pos++ }
            }
        }
    }
}

// ─── Plain text / code parser ────────────────────────────────

private fun parseTextBlocks(source: String, format: BookFormat): List<TextBlock> {
    val isCode = format == BookFormat.CODE || format == BookFormat.JSON ||
        format == BookFormat.XML

    // CSV/TSV → parse as table
    if (format == BookFormat.CSV) {
        return parseCsvToTable(source)
    }

    return if (isCode) {
        listOf(TextBlock.CodeBlock(source, formatToLanguageHint(format)))
    } else {
        val blocks = mutableListOf<TextBlock>()
        val buf = mutableListOf<String>()
        for (line in source.lines()) {
            if (line.isBlank()) {
                if (buf.isNotEmpty()) { blocks.add(TextBlock.Paragraph(AnnotatedString(buf.joinToString(" ")))); buf.clear() }
            } else { buf.add(line.trim()) }
        }
        if (buf.isNotEmpty()) blocks.add(TextBlock.Paragraph(AnnotatedString(buf.joinToString(" "))))
        if (blocks.isEmpty()) blocks.add(TextBlock.Paragraph(AnnotatedString("(empty file)")))
        blocks
    }
}

private fun parseCsvToTable(source: String): List<TextBlock> {
    val lines = source.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return listOf(TextBlock.Paragraph(AnnotatedString("(Empty file)")))

    val delimiter = if (lines.first().count { it == '\t' } > lines.first().count { it == ',' }) '\t' else ','
    val parsed = lines.map { line ->
        line.split(delimiter).map { it.trim().trimStart('"').trimEnd('"') }
    }

    val headers = parsed.first()
    val rows = if (parsed.size > 1) parsed.subList(1, parsed.size) else emptyList()

    // Normalize column count
    val maxCols = (listOf(headers) + rows).maxOf { it.size }
    val normHeaders = headers + List(maxCols - headers.size) { "" }
    val normRows = rows.map { row -> row + List(maxCols - row.size) { "" } }

    return listOf(TextBlock.Table(normHeaders, normRows))
}

private fun formatToLanguageHint(format: BookFormat): String? = when (format) {
    BookFormat.JSON -> "json"; BookFormat.XML -> "xml"; BookFormat.CSV -> "csv"; BookFormat.HTML -> "html"; else -> null
}
