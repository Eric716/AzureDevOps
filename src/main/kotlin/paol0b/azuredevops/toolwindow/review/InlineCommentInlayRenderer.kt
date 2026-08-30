package paol0b.azuredevops.toolwindow.review

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
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
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val metrics = editor.contentComponent.getFontMetrics(font)
        val widest = (listOf(header) + bodyLines).maxOfOrNull(metrics::stringWidth) ?: 0
        val desired = widest + horizontalPadding * 2
        val available = (editor.scrollingModel.visibleArea.width - JBUI.scale(48))
            .coerceAtLeast(JBUI.scale(320))
        return desired.coerceIn(JBUI.scale(320), available)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val lineHeight = editor.contentComponent.getFontMetrics(font).height
        return verticalPadding * 2 + lineHeight * (1 + bodyLines.size) + lineGap * bodyLines.size
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

            val plainFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            val boldFont = plainFont.deriveFont(Font.BOLD)
            val lineHeight = g2.getFontMetrics(plainFont).height
            var baseline = y + verticalPadding + g2.getFontMetrics(boldFont).ascent
            val textX = x + horizontalPadding

            g2.font = boldFont
            g2.color = if (active) ACTIVE_HEADER else RESOLVED_HEADER
            drawClipped(g2, header, textX, baseline, width - horizontalPadding * 2)

            g2.font = plainFont
            g2.color = BODY_FOREGROUND
            bodyLines.forEach { line ->
                baseline += lineHeight + lineGap
                drawClipped(g2, line, textX, baseline, width - horizontalPadding * 2)
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
                .let { if (it.length > MAX_CONTENT_LENGTH) it.take(MAX_CONTENT_LENGTH - 1) + "…" else it }
            "$author: $content"
        }.toMutableList()

        val hidden = comments.size - visible.size
        if (hidden > 0) visible += "+$hidden more ${if (hidden == 1) "reply" else "replies"}…"
        if (visible.isEmpty()) visible += "No visible text in this thread"
        return visible
    }

    private fun drawClipped(g: Graphics2D, text: String, x: Int, baseline: Int, maxWidth: Int) {
        val metrics = g.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) {
            g.drawString(text, x, baseline)
            return
        }

        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (metrics.stringWidth(text.take(mid) + "…") <= maxWidth) low = mid else high = mid - 1
        }
        g.drawString(text.take(low) + "…", x, baseline)
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
