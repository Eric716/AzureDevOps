package paol0b.azuredevops.toolwindow.review

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.FontFallbackIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import paol0b.azuredevops.model.CommentThread
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import kotlin.math.ceil

/** Compact, always-visible preview for one Azure DevOps inline comment thread. */
internal class InlineCommentInlayRenderer(
    private val editor: Editor,
    thread: CommentThread
) : EditorCustomElementRenderer {

    private val active = thread.isActive()
    private val comments = thread.comments.orEmpty()
        .filter { it.isDeleted != true && !it.content.isNullOrBlank() }
    private val header = buildString {
        append(if (active) "Open thread" else "Resolved thread")
        append("  •  ")
        append(comments.size)
        append(if (comments.size == 1) " comment" else " comments")
        append("  •  Click to open")
    }
    private val bodyLines = buildBodyLines()

    private val horizontalPadding = JBUI.scale(12)
    private val verticalPadding = JBUI.scale(8)
    private val lineGap = JBUI.scale(3)

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val fontRenderContext = componentFontRenderContext()
        val widest = styledLines().maxOfOrNull { line ->
            ceil(textWidth(line.text, line.style, fontRenderContext)).toInt()
        } ?: 0
        val desired = widest + horizontalPadding * 2
        val available = (editor.scrollingModel.visibleArea.width - JBUI.scale(48))
            .coerceAtLeast(JBUI.scale(320))
        return desired.coerceIn(JBUI.scale(320), available)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val fontRenderContext = componentFontRenderContext()
        val lines = styledLines()
        return verticalPadding * 2 +
            lines.sumOf { lineMetrics(it.text, it.style, fontRenderContext).height } +
            lineGap * (lines.size - 1).coerceAtLeast(0)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        val g2 = g.create() as Graphics2D
        try {
            GraphicsUtil.setupAntialiasing(g2)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val x = targetRegion.x + JBUI.scale(4)
            val y = targetRegion.y + JBUI.scale(2)
            val width = (targetRegion.width - JBUI.scale(8)).coerceAtLeast(1)
            val height = (targetRegion.height - JBUI.scale(4)).coerceAtLeast(1)

            g2.color = if (active) ACTIVE_BACKGROUND else RESOLVED_BACKGROUND
            g2.fillRoundRect(x, y, width, height, JBUI.scale(8), JBUI.scale(8))
            g2.color = if (active) ACTIVE_BORDER else RESOLVED_BORDER
            g2.drawRoundRect(x, y, width - 1, height - 1, JBUI.scale(8), JBUI.scale(8))

            val textX = x + horizontalPadding
            val maxTextWidth = width - horizontalPadding * 2
            var lineTop = y + verticalPadding
            styledLines().forEachIndexed { index, line ->
                val metrics = lineMetrics(line.text, line.style, g2.fontRenderContext)
                val baseline = lineTop + metrics.ascent
                g2.color = line.color
                drawClipped(g2, line.text, textX, baseline, maxTextWidth, line.style)
                lineTop += metrics.height
                if (index < bodyLines.size) lineTop += lineGap
            }
        } finally {
            g2.dispose()
        }
    }

    private fun buildBodyLines(): List<String> {
        val visible = comments.take(MAX_VISIBLE_COMMENTS).map { comment ->
            val author = comment.author?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
            val content = comment.content.orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()
                .let { truncateAtGraphemeBoundary(it, MAX_CONTENT_LENGTH) }
            "$author: $content"
        }.toMutableList()

        val hidden = comments.size - visible.size
        if (hidden > 0) visible += "+$hidden more ${if (hidden == 1) "reply" else "replies"}…"
        if (visible.isEmpty()) visible += "No visible text in this thread"
        return visible
    }

    private fun styledLines(): List<StyledLine> =
        listOf(
            StyledLine(
                header,
                Font.BOLD,
                if (active) ACTIVE_HEADER else RESOLVED_HEADER
            )
        ) + bodyLines.map { StyledLine(it, Font.PLAIN, BODY_FOREGROUND) }

    private fun drawClipped(
        g: Graphics2D,
        text: String,
        x: Int,
        baseline: Int,
        maxWidth: Int,
        style: Int
    ) {
        if (text.isEmpty() || maxWidth <= 0) return

        if (textWidth(text, style, g.fontRenderContext) <= maxWidth) {
            drawText(g, text, x, baseline, style)
            return
        }

        val boundaries = graphemeBoundaries(text)
        var bestEnd = 0
        var low = 0
        var high = boundaries.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val end = boundaries[middle]
            if (textWidth(text.substring(0, end) + ELLIPSIS, style, g.fontRenderContext) <= maxWidth) {
                bestEnd = end
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        val clipped = if (bestEnd > 0) text.substring(0, bestEnd) + ELLIPSIS else ELLIPSIS
        if (textWidth(clipped, style, g.fontRenderContext) <= maxWidth) {
            drawText(g, clipped, x, baseline, style)
        }
    }

    private fun drawText(g: Graphics2D, text: String, x: Int, baseline: Int, style: Int) {
        var cursorX = x.toFloat()
        fontRuns(text, style, g.fontRenderContext).forEach { run ->
            val layout = TextLayout(text.substring(run.start, run.end), run.font, g.fontRenderContext)
            layout.draw(g, cursorX, baseline.toFloat())
            cursorX += layout.advance
        }
    }

    private fun textWidth(text: String, style: Int, fontRenderContext: FontRenderContext): Float =
        fontRuns(text, style, fontRenderContext).sumOf { run ->
            TextLayout(text.substring(run.start, run.end), run.font, fontRenderContext).advance.toDouble()
        }.toFloat()

    private fun lineMetrics(text: String, style: Int, fontRenderContext: FontRenderContext): PixelLineMetrics {
        val source = text.ifEmpty { " " }
        val runs = fontRuns(source, style, fontRenderContext)
        var maxAscent = 0f
        var maxDescentAndLeading = 0f
        runs.forEach { run ->
            val layout = TextLayout(source.substring(run.start, run.end), run.font, fontRenderContext)
            maxAscent = maxOf(maxAscent, layout.ascent)
            maxDescentAndLeading = maxOf(maxDescentAndLeading, layout.descent + layout.leading)
        }
        return PixelLineMetrics(
            ascent = ceil(maxAscent).toInt(),
            descentAndLeading = ceil(maxDescentAndLeading).toInt()
        )
    }

    private fun fontRuns(text: String, style: Int, fontRenderContext: FontRenderContext): List<FontRun> {
        if (text.isEmpty()) return emptyList()

        val iterator = FontFallbackIterator()
        iterator.setPreferredFonts(editor.colorsScheme.fontPreferences)
        iterator.setFontStyle(style)
        iterator.setFontRenderContext(fontRenderContext)
        iterator.start(text, 0, text.length)

        val runs = mutableListOf<FontRun>()
        while (!iterator.atEnd()) {
            runs += FontRun(iterator.start, iterator.end, iterator.font)
            iterator.advance()
        }
        return runs
    }

    private fun componentFontRenderContext(): FontRenderContext =
        editor.contentComponent
            .getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            .fontRenderContext

    private data class StyledLine(val text: String, val style: Int, val color: Color)
    private data class FontRun(val start: Int, val end: Int, val font: Font)
    private data class PixelLineMetrics(val ascent: Int, val descentAndLeading: Int) {
        val height: Int = ascent + descentAndLeading
    }

    private companion object {
        const val MAX_VISIBLE_COMMENTS = 3
        const val MAX_CONTENT_LENGTH = 180

        val ACTIVE_BACKGROUND = JBColor(Color(240, 246, 255), Color(39, 49, 63))
        val ACTIVE_BORDER = JBColor(Color(137, 174, 225), Color(79, 112, 154))
        val ACTIVE_HEADER = JBColor(Color(36, 91, 160), Color(145, 190, 245))
        val RESOLVED_BACKGROUND = JBColor(Color(241, 249, 242), Color(38, 57, 43))
        val RESOLVED_BORDER = JBColor(Color(139, 190, 146), Color(77, 125, 84))
        val RESOLVED_HEADER = JBColor(Color(49, 113, 59), Color(145, 207, 154))
        val BODY_FOREGROUND = JBColor(Color(43, 45, 48), Color(223, 225, 229))
    }
}
