package io.legado.app.api

import android.net.Uri
import splitties.init.appCtx
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Sends a content-free wake-up hint after Legado has published an exact readable boundary.
 *
 * Operit treats this broadcast as untrusted and re-queries ReaderProvider before doing any work.
 * No book identity, position, or novel text leaves Legado through this broadcast.
 */
internal object OperitReadingCompanionBridge {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (ignored in pending) {
                delay(250)
                // Rapid page turns only need one content-free hint; Operit re-reads the latest boundary.
                while (pending.tryReceive().isSuccess) { }
                notifyProviders()
            }
        }
    }
    fun notifyReadingProgressChanged() {
        pending.trySend(Unit)
    }

    private fun notifyProviders() {
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
