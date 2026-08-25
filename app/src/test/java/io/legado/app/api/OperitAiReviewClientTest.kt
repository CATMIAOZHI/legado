package io.legado.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperitAiReviewClientTest {

    @Test
    fun `paragraph contract filters blank entries and keeps review ids stable`() {
        val contract = OperitReviewParagraphContractSupport.fromTextList(
            listOf("第一段", "", "第二段"),
        )!!

        assertEquals(
            listOf(1, 2),
            contract.paragraphs.map(OperitReviewParagraph::id),
        )
        assertEquals(listOf("第一段", "第二段"), contract.paragraphs.map(OperitReviewParagraph::text))
        assertEquals(64, contract.hash.length)
    }

    @Test
    fun `paragraph contract rejects special layout commands`() {
        assertNull(
            OperitReviewParagraphContractSupport.fromTextList(
                listOf("第一段", "[newpage]", "第二段"),
            ),
        )
        assertNull(
            OperitReviewParagraphContractSupport.fromTextList(
                listOf("第一段\n第二段"),
            ),
        )
    }
}
