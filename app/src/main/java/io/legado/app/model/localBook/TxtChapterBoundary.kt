package io.legado.app.model.localBook

/** All offsets here are bytes, not Kotlin string indices. */
internal data class TxtChapterBoundary(val titleStart: Long, val bodyStart: Long)

internal fun txtChapterBoundary(
    blockStart: Long,
    consumedBytes: Long,
    contentBytes: Long,
    titleBytes: Long,
): TxtChapterBoundary {
    val titleStart = blockStart + consumedBytes + contentBytes
    return TxtChapterBoundary(titleStart, titleStart + titleBytes)
}

internal data class TxtChapterRange(val index: Int, val start: Long, val end: Long)

internal fun remapTxtPosition(
    oldStart: Long,
    oldEnd: Long?,
    position: Int,
    chapters: List<TxtChapterRange>,
): Pair<Int, Int>? {
    val target = chapters.firstOrNull { oldStart >= it.start && oldStart < it.end }
        ?: chapters.firstOrNull { it.start >= oldStart }
        ?: chapters.lastOrNull()
        ?: return null
    return target.index to if (oldStart == target.start && oldEnd == target.end) position else 0
}
