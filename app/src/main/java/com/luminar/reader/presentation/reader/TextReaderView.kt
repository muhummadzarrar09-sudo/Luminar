// app/src/main/java/com/luminar/reader/presentation/reader/TextReaderView.kt
package com.luminar.reader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                is TextBlock.NumberedItem -> block.spans.text
                is TextBlock.Quote -> block.spans.text
                is TextBlock.PlainLine -> block.text
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
                    start = 20.dp,
                    end = 20.dp,
                    top = if (isSearchActive) 120.dp else 72.dp,
                    bottom = 120.dp
                )
            ) {
                items(blocks.size) { index ->
                    val block = blocks[index]
                    val isThisTheCurrentMatch = isSearchActive &&
                        currentMatchBlockIndex == index
                    RenderBlock(
                        block = block,
                        textColor = textColor,
                        theme = theme,
                        fontScale = fontScale,
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
    data class NumberedItem(val number: String, val spans: AnnotatedString) : TextBlock
    data class Quote(val spans: AnnotatedString) : TextBlock
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
    searchQuery: String,
    isCurrentMatch: Boolean
) {
    val scale = fontScale.multiplier
    val highlightColor = LuminarGold.copy(alpha = 0.3f)
    val currentHighlightColor = LuminarGold.copy(alpha = 0.65f)

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
                    .padding(vertical = 6.dp),
                color = textColor,
                fontSize = 15.sp * scale,
                lineHeight = 24.sp * scale
            )
        }

        is TextBlock.CodeBlock -> {
            val codeBg = when (theme) {
                AppTheme.DARK_AMOLED -> Color(0xFF1A1A1A)
                AppTheme.SEPIA -> Color(0xFFE2D5B5)
                AppTheme.LIGHT -> Color(0xFFEEEEEE)
            }
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

        is TextBlock.BulletItem -> {
            Text(
                text = buildAnnotatedString {
                    append("  ".repeat(block.indent))
                    append("•  ")
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
                blocks.add(TextBlock.BulletItem(parseInlineMarkdown(text), indent))
                i++
            }
            trimmed.matches(OL_REGEX) -> {
                val match = OL_CAPTURE.find(trimmed)
                if (match != null) {
                    blocks.add(TextBlock.NumberedItem(match.groupValues[1], parseInlineMarkdown(match.groupValues[2])))
                }
                i++
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
        format == BookFormat.XML || format == BookFormat.CSV
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

private fun formatToLanguageHint(format: BookFormat): String? = when (format) {
    BookFormat.JSON -> "json"; BookFormat.XML -> "xml"; BookFormat.CSV -> "csv"; BookFormat.HTML -> "html"; else -> null
}
