package io.legado.app.api.controller

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import io.legado.app.api.ReturnData
import io.legado.app.api.OperitReviewParagraphContractSupport
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.CacheManager
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadableSnapshotStore
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.cnCompare
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

private val windowsDrivePrefix = Regex("^[A-Za-z]:")

internal fun requireSafeUploadedBookFileName(fileName: String): String {
    val isUnsafe = fileName.isBlank() ||
            fileName == "." ||
            fileName == ".." ||
            fileName.indexOfAny(charArrayOf('/', '\\')) >= 0 ||
            windowsDrivePrefix.containsMatchIn(fileName) ||
            fileName.any { Character.isISOControl(it) }
    require(!isUnsafe) { "Invalid uploaded book file name" }
    return fileName
}

object BookController {

    private data class ImageContext(
        val bookUrl: String,
        val book: Book,
        val bookSource: BookSource?,
    )

    private data class ReadingBoundarySnapshot(
        val bookUrl: String,
        val chapterIndex: Int,
        val chapterTitle: String?,
        val layoutPosition: Int,
        val layoutTitleLength: Int?,
        val bodyPosition: Int?,
        val bodyContent: String?,
        val capturedAt: Long,
    )

    private data class ReadingSnapshotData(
        val bookUrl: String,
        val name: String,
        val author: String,
        val totalChapterNum: Int,
        val currentChapterIndex: Int,
        val currentChapterTitle: String?,
        val layoutPosition: Int,
        val bodyPosition: Int?,
        val preciseBodyPositionAvailable: Boolean,
        val lastReadAt: Long,
        val capturedAt: Long,
    )

    private data class ReadableChapterData(
        val bookUrl: String,
        val chapterUrl: String,
        val chapterIndex: Int,
        val chapterTitle: String?,
        val content: String,
        val readableUntil: Int,
        val isComplete: Boolean,
        val readingChapterIndex: Int,
        val capturedAt: Long,
    )

    private data class AnnotationParagraphData(
        val reviewId: Int,
        val text: String,
    )

    private data class AnnotationChapterData(
        val bookUrl: String,
        val chapterUrl: String,
        val chapterIndex: Int,
        val chapterTitle: String?,
        val contractHash: String,
        val paragraphs: List<AnnotationParagraphData>,
        val capturedAt: Long,
    )

    @Volatile
    private var cachedImageContext: ImageContext? = null
    private val defaultCoverCache by lazy { WeakHashMap<Drawable, Bitmap>() }

    /**
     * 书架所有书籍
     */
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("还没有添加小说")
            } else {
                val data = when (AppConfig.bookshelfSort) {
                    1 -> books.sortedByDescending { it.latestChapterTime }
                    2 -> books.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> books.sortedBy { it.order }
                    else -> books.sortedByDescending { it.durChapterTime }
                }
                returnData.setData(data)
            }
        }

    /**
     * 获取封面
     */
    fun getCover(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val coverPath = parameters["path"]?.firstOrNull()
        val ftBitmap = ImageLoader.loadBitmap(appCtx, coverPath)
            .override(84, 112)
            .centerCrop()
            .submit()
        return try {
            returnData.setData(ftBitmap.get(3, TimeUnit.SECONDS))
        } catch (e: Exception) {
            try {
                val defaultBitmap = defaultCoverCache.getOrPut(BookCover.defaultDrawable) {
                    Glide.with(appCtx)
                        .asBitmap()
                        .load(BookCover.defaultDrawable.toBitmap())
                        .override(84, 112)
                        .centerCrop()
                        .submit()
                        .get()
                }
                returnData.setData(defaultBitmap)
            } catch (e: Exception) {
                returnData.setErrorMsg(e.localizedMessage ?: "getCover error")
            }
        }
    }

    /**
     * 获取正文图片
     */
    fun getImg(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookUrl = parameters["url"]?.firstOrNull()
        if (bookUrl.isNullOrBlank()) {
            return returnData.setErrorMsg("bookUrl为空")
        }
        val src = parameters["path"]?.firstOrNull()
            ?: return returnData.setErrorMsg("图片链接为空")
        val width = parameters["width"]?.firstOrNull()?.toInt() ?: 640
        val cachedContext = cachedImageContext
        val imageContext = cachedContext?.takeIf { it.bookUrl == bookUrl }
            ?: appDb.bookDao.getBook(bookUrl)?.let { book ->
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                ImageContext(bookUrl, book, bookSource).also {
                    cachedImageContext = it
                }
            } ?: return returnData.setErrorMsg("bookUrl不对")
        val bitmap = runBlocking {
            ImageProvider.cacheImage(imageContext.book, src, imageContext.bookSource)
            ImageProvider.getImage(imageContext.book, src, width)
        }
        return returnData.setData(bitmap)
    }

    /**
     * 更新目录
     */
    fun refreshToc(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        try {
            val bookUrl = parameters["url"]?.firstOrNull()
            if (bookUrl.isNullOrEmpty()) {
                return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
            }
            val book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("未在数据库找到对应书籍，请先添加")
            if (book.isLocal) {
                val toc = LocalBook.getChapterList(book)
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                book.update()
                return returnData.setData(toc)
            } else {
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: return returnData.setErrorMsg("未找到对应书源,请换源")
                val toc = runBlocking {
                    if (book.tocUrl.isBlank()) {
                        WebBook.getBookInfoAwait(bookSource, book)
                    }
                    WebBook.getChapterListAwait(bookSource, book).getOrThrow()
                }
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                book.update()
                return returnData.setData(toc)
            }
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "refresh toc error")
        }
    }

    /**
     * 获取目录
     */
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        val chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapterList.isEmpty()) {
            return refreshToc(parameters)
        }
        return returnData.setData(chapterList)
    }

    /**
     * 获取正文
     */
    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val index = parameters["index"]?.firstOrNull()?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        if (index == null) {
            return returnData.setErrorMsg("参数index不能为空, 请指定目录序号")
        }
        val book = appDb.bookDao.getBook(bookUrl)
        val chapter = runBlocking {
            var chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
            var wait = 0
            while (chapter == null && wait < 30) {
                delay(1000)
                chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
                wait++
            }
            chapter
        }
        if (book == null || chapter == null) {
            return returnData.setErrorMsg("未找到")
        }
        return try {
            val content = runBlocking { loadProcessedBookContent(book, chapter) }
            returnData.setData(content.toString())
        } catch (e: Exception) {
            returnData.setErrorMsg(e.stackTraceStr)
        }
    }

    /**
     * Returns the explicit paragraph contract used by Operit's isolated next-chapter generator.
     * Unsupported special-layout chapters fail closed instead of exposing mismatched review IDs.
     */
    fun getAnnotationBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return ReturnData().setErrorMsg("参数url不能为空，请指定书籍地址")
        val chapterIndex = parameters["index"]?.firstOrNull()?.toIntOrNull()
            ?: return ReturnData().setErrorMsg("参数index不能为空, 请指定目录序号")
        val book = appDb.bookDao.getBook(bookUrl)
            ?: return ReturnData().setErrorMsg("未找到书籍")
        val chapter = runBlocking {
            var value = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
            var wait = 0
            while (value == null && wait < 30) {
                delay(1000)
                value = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
                wait++
            }
            value
        } ?: return ReturnData().setErrorMsg("未找到章节")
        return try {
            val content = runBlocking { loadProcessedBookContent(book, chapter) }
            val contract = OperitReviewParagraphContractSupport.fromTextList(content.textList)
                ?: return ReturnData().setErrorMsg("该章节使用特殊排版，暂不生成 AI 段评")
            val chapterTitle = chapter.getDisplayTitle(
                ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                book.getUseReplaceRule(),
                replaceBook = book.toReplaceBook(),
            )
            ReturnData().setData(
                AnnotationChapterData(
                    bookUrl = bookUrl,
                    chapterUrl = chapter.url,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    contractHash = contract.hash,
                    paragraphs = contract.paragraphs.map { paragraph ->
                        AnnotationParagraphData(
                            reviewId = paragraph.id,
                            text = paragraph.text,
                        )
                    },
                    capturedAt = System.currentTimeMillis(),
                )
            )
        } catch (error: Exception) {
            ReturnData().setErrorMsg(error.stackTraceStr)
        }
    }

    /**
     * Returns the most recently read book and a body-only reading position when the active
     * chapter layout is still available. The body position is intentionally nullable: callers
     * must not derive it from durChapterPos because that value includes the rendered title.
     */
    fun getReadingSnapshot(parameters: Map<String, List<String>>): ReturnData {
        val requestedBookUrl = parameters["url"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val book = if (requestedBookUrl != null) {
            appDb.bookDao.getBook(requestedBookUrl)
                ?: return ReturnData().setErrorMsg("未找到指定书籍")
        } else {
            ReadBook.liveReadableBoundary?.let { live ->
                appDb.bookDao.getBook(live.bookUrl)
            } ?: appDb.bookDao.all.maxByOrNull { it.durChapterTime }
                ?: return ReturnData().setErrorMsg("没有最近阅读书籍")
        }
        val boundary = captureReadingBoundary(book)
        return ReturnData().setData(
            ReadingSnapshotData(
                bookUrl = book.bookUrl,
                name = book.name,
                author = book.author,
                totalChapterNum = book.totalChapterNum,
                currentChapterIndex = boundary.chapterIndex,
                currentChapterTitle = boundary.chapterTitle,
                layoutPosition = boundary.layoutPosition,
                bodyPosition = boundary.bodyPosition,
                preciseBodyPositionAvailable = boundary.bodyPosition != null,
                lastReadAt = book.durChapterTime,
                capturedAt = boundary.capturedAt,
            )
        )
    }

    /**
     * Returns only content that is readable under the latest Legado reading boundary.
     *
     * Completed chapters are returned in full. The current chapter is returned only when its
     * active layout exposes a trustworthy title length, and is truncated before leaving Legado.
     * Future chapters are rejected before content loading. The boundary is captured again after
     * loading so a concurrent backward progress change also fails closed.
     */
    fun getReadableBookContent(parameters: Map<String, List<String>>, cachedOnly: Boolean = false): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return ReturnData().setErrorMsg("参数url不能为空，请指定书籍地址")
        val chapterIndex = parameters["index"]?.firstOrNull()?.toIntOrNull()
            ?: return ReturnData().setErrorMsg("参数index不能为空, 请指定目录序号")
        val book = appDb.bookDao.getBook(bookUrl)
            ?: return ReturnData().setErrorMsg("未找到书籍")
        val sourceChapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
            ?: return ReturnData().setErrorMsg("未找到章节")
        val initialBoundary = captureReadingBoundary(book)
        if (
            appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)?.url != sourceChapter.url
        ) {
            return ReturnData().setErrorMsg("目录已更新，请重试读取章节")
        }
        if (chapterIndex > initialBoundary.chapterIndex) {
            return ReturnData().setErrorMsg("拒绝读取未读章节")
        }
        if (chapterIndex == initialBoundary.chapterIndex && initialBoundary.bodyPosition == null) {
            return ReturnData().setErrorMsg("当前章节的安全正文位置暂不可用")
        }

        val initiallyCurrent = chapterIndex == initialBoundary.chapterIndex
        val initialContent = if (initiallyCurrent) {
            initialBoundary.bodyContent
                ?: return ReturnData().setErrorMsg("当前章节的安全正文暂不可用")
        } else {
            try {
                if (cachedOnly) {
                    val cached = BookHelp.getContent(book, sourceChapter)
                        ?: return ReturnData().setData(mapOf("cached" to false))
                    ContentProcessor.get(book.name, book.origin)
                        .getContent(book, sourceChapter, cached, includeTitle = false).toString()
                } else {
                    runBlocking { loadProcessedBookContent(book, sourceChapter) }.toString()
                }
            } catch (error: Exception) {
                return ReturnData().setErrorMsg(error.stackTraceStr)
            }
        }

        val latestBook = appDb.bookDao.getBook(bookUrl)
            ?: return ReturnData().setErrorMsg("未找到书籍")
        val latestBoundary = captureReadingBoundary(latestBook)
        if (chapterIndex > latestBoundary.chapterIndex) {
            return ReturnData().setErrorMsg("阅读进度已后退，拒绝返回该章节")
        }

        val (safeContent, readableUntil) = when {
            chapterIndex == latestBoundary.chapterIndex -> {
                val latestContent = latestBoundary.bodyContent
                    ?: return ReturnData().setErrorMsg("当前章节的安全正文暂不可用")
                val latestPosition = latestBoundary.bodyPosition
                    ?.coerceIn(0, latestContent.length)
                    ?: return ReturnData().setErrorMsg("当前章节的安全正文位置暂不可用")
                latestContent to latestPosition
            }
            initiallyCurrent -> {
                val initialPosition = initialBoundary.bodyPosition
                    ?.coerceIn(0, initialContent.length)
                    ?: return ReturnData().setErrorMsg("当前章节的安全正文位置暂不可用")
                initialContent to initialPosition
            }
            else -> initialContent to initialContent.length
        }
        val latestChapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
            ?: return ReturnData().setErrorMsg("未找到章节")
        if (latestChapter.url != sourceChapter.url) {
            return ReturnData().setErrorMsg("目录已更新，请重试读取章节")
        }
        val chapterTitle = sourceChapter.getDisplayTitle(
            ContentProcessor.get(latestBook.name, latestBook.origin).getTitleReplaceRules(),
            latestBook.getUseReplaceRule(),
            replaceBook = latestBook.toReplaceBook(),
        )
        return ReturnData().setData(
            ReadableChapterData(
                bookUrl = bookUrl,
                chapterUrl = sourceChapter.url,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                content = safeContent.take(readableUntil),
                readableUntil = readableUntil,
                isComplete = chapterIndex < latestBoundary.chapterIndex,
                readingChapterIndex = latestBoundary.chapterIndex,
                capturedAt = latestBoundary.capturedAt,
            )
        )
    }

    private fun captureReadingBoundary(book: Book): ReadingBoundarySnapshot {
        /*
         * saveRead publishes this immutable object synchronously before its database write is
         * queued. A Binder query therefore sees an immediate backward seek and never combines
         * independently mutable ReadBook fields. Persisted data is only the process-death fallback.
         */
        val liveBoundary = ReadBook.liveReadableBoundary?.takeIf {
            it.bookUrl == book.bookUrl
        }
        val chapterIndex = liveBoundary?.chapterIndex ?: book.durChapterIndex
        val layoutPosition = liveBoundary?.layoutPosition ?: book.durChapterPos
        val readableSnapshot = if (liveBoundary != null) {
            liveBoundary.safeSnapshot?.takeIf {
                it.chapterIndex == chapterIndex &&
                    it.layoutPosition == layoutPosition
            }
        } else {
            ReadableSnapshotStore.load(book.bookUrl)?.takeIf {
                it.chapterIndex == chapterIndex &&
                    it.layoutPosition == layoutPosition
            }
        }
        val resolvedBodyPosition = readableSnapshot?.bodyPosition
        val resolvedBodyContent = readableSnapshot?.content
        val chapterTitle = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.title
            ?: if (chapterIndex == book.durChapterIndex) book.durChapterTitle else null
        return ReadingBoundarySnapshot(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            layoutPosition = layoutPosition,
            layoutTitleLength = readableSnapshot?.let { layoutPosition - it.bodyPosition },
            bodyPosition = resolvedBodyPosition,
            bodyContent = resolvedBodyContent,
            capturedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun loadProcessedBookContent(
        book: Book,
        chapter: io.legado.app.data.entities.BookChapter,
    ): BookContent {
        val rawContent = BookHelp.getContent(book, chapter) ?: run {
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                ?: error("未找到书源")
            WebBook.getContentAwait(bookSource, book, chapter)
        }
        return ContentProcessor.get(book.name, book.origin)
            .getContent(book, chapter, rawContent, includeTitle = false)
    }

    /**
     * 保存书籍
     */
    suspend fun saveBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            AppWebDav.uploadBookProgress(book)
            book.save()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 删除书籍
     */
    fun deleteBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            book.delete()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 保存进度
     */
    suspend fun saveBookProgress(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<BookProgress>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull()?.let { bookProgress ->
                appDb.bookDao.getBook(bookProgress.name, bookProgress.author)?.let { book ->
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    AppWebDav.uploadBookProgress(bookProgress) {
                        book.syncTime = System.currentTimeMillis()
                    }
                    book.update()
                    ReadBook.book?.let {
                        if (it.name == bookProgress.name &&
                            it.author == bookProgress.author
                        ) {
                            ReadBook.webBookProgress = bookProgress
                        }
                    }
                    return returnData.setData("")
                }
            }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 添加本地书籍
     */
    fun addLocalBook(
        parameters: Map<String, List<String>>,
        files: Map<String, String>
    ): ReturnData {
        val returnData = ReturnData()
        val rawFileName = parameters["fileName"]?.firstOrNull()
            ?: return returnData.setErrorMsg("fileName 不能为空")
        val fileName = kotlin.runCatching {
            requireSafeUploadedBookFileName(rawFileName)
        }.getOrElse {
            return returnData.setErrorMsg("fileName 格式不正确")
        }
        val fileData = files["fileData"]
            ?: return returnData.setErrorMsg("fileData 不能为空")
        kotlin.runCatching {
            val uri = LocalBook.saveBookFile(File(fileData).inputStream(), fileName)
            LocalBook.importFile(uri)
        }.onFailure {
            return when (it) {
                is SecurityException -> returnData.setErrorMsg("需重新设置书籍保存位置!")
                else -> returnData.setErrorMsg("保存书籍错误\n${it.localizedMessage}")
            }
        }
        return returnData.setData(true)
    }

    /**
     * 保存web阅读界面配置
     */
    fun saveWebReadConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData?.let {
            CacheManager.put("webReadConfig", postData)
        } ?: CacheManager.delete("webReadConfig")
        return returnData.setData("")
    }

    /**
     * 获取web阅读界面配置
     */
    fun getWebReadConfig(): ReturnData {
        val returnData = ReturnData()
        val data = CacheManager.get("webReadConfig")
            ?: return returnData.setErrorMsg("没有配置")
        return returnData.setData(data)
    }

}
