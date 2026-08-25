package io.legado.app.api.controller

/**
 * Converts Legado's persisted layout position into the body-only coordinate used by
 * getBookContent. A negative title length means that the active chapter layout is unavailable;
 * callers must fail closed instead of guessing.
 */
internal fun resolveReadableBodyEnd(
    layoutPosition: Int,
    layoutTitleLength: Int,
    bodyLength: Int,
): Int? {
    if (layoutTitleLength < 0) return null
    return (layoutPosition - layoutTitleLength).coerceIn(0, bodyLength.coerceAtLeast(0))
}
