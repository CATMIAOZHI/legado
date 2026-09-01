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
        assertEquals(
            "44c69e68d1a00ebe6faf9e494048e12147656b6555c17f0a0e1251ae3cc5b04e",
            contract.hash,
        )
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

    @Test
    fun `paragraph fingerprint is exact and shared with Operit`() {
        assertEquals(
            "208a07e6199994f4e9e1442ba9fc8202ae30a0ed619b338c2c277ba16a8b7b8e",
            OperitReviewParagraphContractSupport.paragraphFingerprint("第一段"),
        )
        assertEquals(
            "ce6cc083c3f135d5ed744b00aedaa36364cb297d18f58c76e58df42c77e23744",
            OperitReviewParagraphContractSupport.paragraphFingerprint("  猫　"),
        )
    }

    @Test
    fun `fingerprint mapping follows unchanged paragraph after insertion`() {
        val keptFingerprint =
            OperitReviewParagraphContractSupport.paragraphFingerprint("保留段")
        val mapped =
            OperitAiReviewMappingSupport.map(
                items =
                    listOf(
                        OperitAiReviewSummaryItem(
                            sourceParagraphIndex = 3,
                            count = 2,
                            preview = "还在这里",
                            paragraphFingerprint = keptFingerprint,
                            sourceUnique = true,
                        ),
                    ),
                currentParagraphFingerprints =
                    mapOf(
                        1 to OperitReviewParagraphContractSupport.paragraphFingerprint("新增段"),
                        2 to keptFingerprint,
                    ),
                remapRequired = true,
            )

        assertEquals(mapOf(2 to 2), mapped.counts)
        assertEquals(mapOf(2 to "还在这里"), mapped.previews)
        assertEquals(mapOf(2 to 3), mapped.sourceParagraphIndices)
    }

    @Test
    fun `fingerprint mapping hides source and target ambiguities`() {
        val duplicateFingerprint =
            OperitReviewParagraphContractSupport.paragraphFingerprint("相同段")
        val current =
            mapOf(
                1 to duplicateFingerprint,
                2 to duplicateFingerprint,
            )

        assertEquals(
            emptyMap<Int, Int>(),
            OperitAiReviewMappingSupport.map(
                items =
                    listOf(
                        OperitAiReviewSummaryItem(
                            sourceParagraphIndex = 1,
                            count = 1,
                            preview = "源内重复",
                            paragraphFingerprint = duplicateFingerprint,
                            sourceUnique = false,
                        ),
                    ),
                currentParagraphFingerprints = mapOf(1 to duplicateFingerprint),
                remapRequired = true,
            ).counts,
        )
        assertEquals(
            emptyMap<Int, Int>(),
            OperitAiReviewMappingSupport.map(
                items =
                    listOf(
                        OperitAiReviewSummaryItem(
                            sourceParagraphIndex = 1,
                            count = 1,
                            preview = "目标内重复",
                            paragraphFingerprint = duplicateFingerprint,
                            sourceUnique = true,
                        ),
                    ),
                currentParagraphFingerprints = current,
                remapRequired = true,
            ).counts,
        )
    }
}
