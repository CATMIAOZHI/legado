package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadableSnapshotStoreTest {

    @Test
    fun `snapshot persists only the safe body prefix`() {
        val snapshot = createReadableSnapshot(
            bookUrl = "book",
            chapterIndex = 4,
            layoutPosition = 13,
            layoutTitleLength = 5,
            bodyContent = "0123456789abcdef",
            capturedAt = 7,
        )

        requireNotNull(snapshot)
        assertEquals(8, snapshot.bodyPosition)
        assertEquals("01234567", snapshot.content)
        assertEquals(13, snapshot.layoutPosition)
    }

    @Test
    fun `unknown layout title length does not create a snapshot`() {
        assertNull(
            createReadableSnapshot(
                bookUrl = "book",
                chapterIndex = 4,
                layoutPosition = 13,
                layoutTitleLength = -1,
                bodyContent = "body",
            )
        )
    }
}
