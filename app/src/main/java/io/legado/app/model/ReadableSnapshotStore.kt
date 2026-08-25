package io.legado.app.model

import android.util.AtomicFile
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

internal data class PersistedReadableSnapshot(
    val bookUrl: String,
    val chapterIndex: Int,
    val layoutPosition: Int,
    val bodyPosition: Int,
    val content: String,
    val capturedAt: Long,
)

internal fun createReadableSnapshot(
    bookUrl: String,
    chapterIndex: Int,
    layoutPosition: Int,
    layoutTitleLength: Int,
    bodyContent: String,
    capturedAt: Long = System.currentTimeMillis(),
): PersistedReadableSnapshot? {
    if (layoutTitleLength < 0) return null
    val bodyPosition = (layoutPosition - layoutTitleLength).coerceIn(0, bodyContent.length)
    return PersistedReadableSnapshot(
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        layoutPosition = layoutPosition,
        bodyPosition = bodyPosition,
        content = bodyContent.take(bodyPosition),
        capturedAt = capturedAt,
    )
}

internal object ReadableSnapshotStore {
    private val directory by lazy {
        File(appCtx.noBackupFilesDir, "reading_companion").apply { mkdirs() }
    }

    fun save(snapshot: PersistedReadableSnapshot) {
        val atomicFile = AtomicFile(fileFor(snapshot.bookUrl))
        val stream = atomicFile.startWrite()
        try {
            stream.write(GSON.toJson(snapshot).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    fun load(bookUrl: String): PersistedReadableSnapshot? {
        val file = fileFor(bookUrl)
        if (!file.isFile) return null
        return runCatching {
            GSON.fromJson(file.readText(), PersistedReadableSnapshot::class.java)
        }.getOrNull()?.takeIf { snapshot ->
            snapshot.bookUrl == bookUrl &&
                snapshot.chapterIndex >= 0 &&
                snapshot.layoutPosition >= 0 &&
                snapshot.bodyPosition >= 0 &&
                snapshot.bodyPosition == snapshot.content.length
        }
    }

    fun invalidate(bookUrl: String) {
        AtomicFile(fileFor(bookUrl)).delete()
    }

    private fun fileFor(bookUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bookUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$digest.json")
    }
}
