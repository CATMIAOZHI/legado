package io.legado.app.model

data class VisibleReadingSnapshot(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val layoutPosition: Int,
    val bodyStart: Int,
    val bodyEnd: Int,
    val content: String,
)

internal fun visibleBodyRange(start: Int, end: Int, titleLength: Int, bodyLength: Int): IntRange? {
    if (titleLength < 0 || start < 0 || end < start) return null
    val bodyStart = (start - titleLength).coerceIn(0, bodyLength)
    val bodyEnd = (end - titleLength).coerceIn(0, bodyLength)
    return bodyStart until bodyEnd
}
