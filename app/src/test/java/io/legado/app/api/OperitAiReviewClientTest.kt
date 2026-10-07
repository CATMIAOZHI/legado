package io.legado.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class OperitAiReviewClientTest {
    @Test fun `unique unchanged paragraph can move but changed ambiguous or unversioned text cannot`() {
        val contract = OperitReviewParagraphContractSupport.fromTextList(listOf("inserted","original"))!!
        val summary = JSONObject().put("contentHash","old").put("remapRequired",true)
            .put("mappingVersion",OperitReviewParagraphContractSupport.MAPPING_VERSION)
        val item = JSONObject().put("paragraphIndex",1).put("sourceUnique",true)
            .put("paragraphFingerprint",OperitReviewParagraphContractSupport.fingerprint("original"))
        assertEquals(2,mapOperitParagraph(summary,item,contract.hash,contract))
        summary.remove("mappingVersion")
        assertNull(mapOperitParagraph(summary,item,contract.hash,contract))
        summary.put("mappingVersion",OperitReviewParagraphContractSupport.MAPPING_VERSION)
        val duplicate = OperitReviewParagraphContractSupport.fromTextList(listOf("original","original"))!!
        assertNull(mapOperitParagraph(summary,item,duplicate.hash,duplicate))
        item.put("sourceUnique",false)
        assertNull(mapOperitParagraph(summary,item,contract.hash,contract))
        item.put("sourceUnique",true).put("paragraphFingerprint",OperitReviewParagraphContractSupport.fingerprint(" original"))
        assertNull(mapOperitParagraph(summary,item,contract.hash,contract))
        summary.put("contentHash",contract.hash)
        assertEquals(1,mapOperitParagraph(summary,item,contract.hash,contract))
        item.put("paragraphIndex",99)
        assertNull(mapOperitParagraph(summary,item,contract.hash,contract))
    }

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
