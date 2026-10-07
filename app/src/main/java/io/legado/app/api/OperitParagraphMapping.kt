package io.legado.app.api

import org.json.JSONObject

internal fun mapOperitParagraph(
    summary: JSONObject,
    item: JSONObject,
    currentHash: String,
    contract: OperitReviewParagraphContract?,
): Int? {
    val source = item.optInt("paragraphIndex")
    if (source <= 0) return null
    if (summary.optString("contentHash") == currentHash) {
        return source.takeIf { contract == null || contract.paragraphs.any { it.id == source } }
    }
    if (contract == null || contract.hash != currentHash ||
        !summary.optBoolean("remapRequired") ||
        summary.optString("mappingVersion") != OperitReviewParagraphContractSupport.MAPPING_VERSION ||
        !item.optBoolean("sourceUnique")) return null
    return contract.uniqueFingerprints[item.optString("paragraphFingerprint")]
}
