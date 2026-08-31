package paol0b.azuredevops.toolwindow.review

import java.text.BreakIterator
import java.util.Locale

internal const val ELLIPSIS = "…"

/** UTF-16 indexes immediately after each user-perceived character. */
internal fun graphemeBoundaries(text: String): List<Int> {
    if (text.isEmpty()) return emptyList()

    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val boundaries = mutableListOf<Int>()
    var boundary = iterator.next()
    while (boundary != BreakIterator.DONE) {
        boundaries += boundary
        boundary = iterator.next()
    }
    return boundaries
}

internal fun truncateAtGraphemeBoundary(text: String, maxGraphemes: Int): String {
    require(maxGraphemes >= 0) { "maxGraphemes must not be negative" }
    if (text.isEmpty()) return text
    if (maxGraphemes == 0) return ELLIPSIS

    val boundaries = graphemeBoundaries(text)
    if (boundaries.size <= maxGraphemes) return text
    return text.substring(0, boundaries[maxGraphemes - 1]) + ELLIPSIS
}
