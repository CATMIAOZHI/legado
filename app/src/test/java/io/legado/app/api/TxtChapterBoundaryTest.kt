package io.legado.app.api

import io.legado.app.model.localBook.txtChapterBoundary
import io.legado.app.model.localBook.TxtChapterRange
import io.legado.app.model.localBook.remapTxtPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TxtChapterBoundaryTest {
    @Test fun `catalog changes preserve matching ranges and reset changed ranges safely`() {
        val chapters = listOf(TxtChapterRange(1, 10, 100), TxtChapterRange(2, 110, 200))
        assertEquals(2 to 45, remapTxtPosition(110, 200, 45, chapters))
        assertEquals(2 to 0, remapTxtPosition(140, 160, 45, chapters))
        assertEquals(1 to 0, remapTxtPosition(0, 0, 45, chapters))
        assertEquals(2 to 0, remapTxtPosition(210, 300, 45, chapters))
    }
    @Test fun `split excludes next title and retains previous tail`() {
        for (charset in listOf(Charsets.UTF_8, Charset.forName("GB18030"))) {
            val firstTitle = "第一章\n"
            val body = "正文\n".repeat(40000)
            val nextTitle = "第二章\n"
            val raw = (firstTitle + body + nextTitle + "结尾\n").toByteArray(charset)
            val start = firstTitle.toByteArray(charset).size.toLong()
            val boundary = txtChapterBoundary(0, start, body.toByteArray(charset).size.toLong(),
                nextTitle.toByteArray(charset).size.toLong())
            assertTrue(boundary.titleStart - start > 102400)
            assertEquals(body, String(raw.copyOfRange(start.toInt(), boundary.titleStart.toInt()), charset))
            assertEquals("结尾\n", String(raw.copyOfRange(boundary.bodyStart.toInt(), raw.size), charset))
        }
    }

    @Test fun `cross block boundary and BOM keep absolute offsets`() {
        assertEquals(512003L + 700, txtChapterBoundary(512003, 0, 700, 12).titleStart)
        assertEquals(512003L + 700 + 12, txtChapterBoundary(512003, 0, 700, 12).bodyStart)
        assertEquals(3L + 12 + 102500, txtChapterBoundary(3, 12, 102500, 9).titleStart)
    }
}
