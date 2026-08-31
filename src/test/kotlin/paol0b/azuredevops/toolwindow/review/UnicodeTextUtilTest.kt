package paol0b.azuredevops.toolwindow.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicodeTextUtilTest {

    @Test
    fun `truncate keeps CJK and supplementary emoji intact`() {
        val result = truncateAtGraphemeBoundary("中文😀測試", 3)

        assertEquals("中文😀…", result)
        assertFalse(result.hasUnpairedSurrogate())
    }

    @Test
    fun `truncate keeps combining characters together`() {
        assertEquals("e\u0301…", truncateAtGraphemeBoundary("e\u0301x", 1))
    }

    @Test
    fun `text within limit is unchanged`() {
        val text = "這是一則 PR 留言，English 123 😀"

        assertEquals(text, truncateAtGraphemeBoundary(text, 100))
        assertTrue(graphemeBoundaries(text).last() == text.length)
    }

    private fun String.hasUnpairedSurrogate(): Boolean {
        var index = 0
        while (index < length) {
            val current = this[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= length || !Character.isLowSurrogate(this[index + 1])) return true
                    index += 2
                }
                Character.isLowSurrogate(current) -> return true
                else -> index++
            }
        }
        return false
    }
}
