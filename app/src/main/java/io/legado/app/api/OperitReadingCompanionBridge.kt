package io.legado.app.api

import android.net.Uri
import splitties.init.appCtx

/**
 * Sends a content-free wake-up hint after Legado has published an exact readable boundary.
 *
 * Operit treats this broadcast as untrusted and re-queries ReaderProvider before doing any work.
 * No book identity, position, or novel text leaves Legado through this broadcast.
 */
internal object OperitReadingCompanionBridge {
    fun notifyReadingProgressChanged() {
        OPERIT_AUTHORITIES.forEach { authority ->
            runCatching {
                appCtx.contentResolver.call(
                    Uri.Builder()
                        .scheme("content")
                        .authority(authority)
                        .build(),
                    METHOD_READING_PROGRESS_CHANGED,
                    null,
                    null,
                )
            }
        }
    }

    private const val METHOD_READING_PROGRESS_CHANGED = "reading_progress_changed"

    private val OPERIT_AUTHORITIES = listOf(
        "com.rainy.operitry.readingCompanionAnnotations",
        "com.rainy.operitry.dev.readingCompanionAnnotations",
        "com.rainy.operitry.clone.readingCompanionAnnotations",
    )
}
