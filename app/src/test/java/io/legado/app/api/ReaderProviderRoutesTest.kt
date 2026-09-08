package io.legado.app.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderProviderRoutesTest {

    @Test
    fun `cache route never dispatches to network capable reader`() {
        val result = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetCachedReadableBookContent,
            mapOf("url" to listOf("book"), "index" to listOf("3")),
            getReadableBookContent = { error("network-capable route must not run") },
            getCachedReadableBookContent = { parameters ->
                assertEquals("3", parameters["index"]?.single())
                "not_downloaded"
            },
        )
        assertEquals("not_downloaded", result)
    }

    @Test
    fun `registered paths resolve to their public request semantics`() {
        val expected = mapOf(
            "bookSource/insert" to ReaderProviderRequestCode.SaveBookSource,
            "bookSources/insert" to ReaderProviderRequestCode.SaveBookSources,
            "bookSources/delete" to ReaderProviderRequestCode.DeleteBookSources,
            "bookSource/query" to ReaderProviderRequestCode.GetBookSource,
            "bookSources/query" to ReaderProviderRequestCode.GetBookSources,
            "rssSource/insert" to ReaderProviderRequestCode.SaveRssSource,
            "rssSources/insert" to ReaderProviderRequestCode.SaveRssSources,
            "rssSources/delete" to ReaderProviderRequestCode.DeleteRssSources,
            "rssSource/query" to ReaderProviderRequestCode.GetRssSource,
            "rssSources/query" to ReaderProviderRequestCode.GetRssSources,
            "book/insert" to ReaderProviderRequestCode.SaveBook,
            "books/query" to ReaderProviderRequestCode.GetBookshelf,
            "book/refreshToc/query" to ReaderProviderRequestCode.RefreshToc,
            "book/chapter/query" to ReaderProviderRequestCode.GetChapterList,
            "book/content/query" to ReaderProviderRequestCode.GetBookContent,
            "book/cover/query" to ReaderProviderRequestCode.GetBookCover,
            "reading/snapshot/query" to ReaderProviderRequestCode.GetReadingSnapshot,
            "book/readableContent/query" to ReaderProviderRequestCode.GetReadableBookContent,
            "book/cachedReadableContent/query" to ReaderProviderRequestCode.GetCachedReadableBookContent,
            "book/annotationContent/query" to ReaderProviderRequestCode.GetAnnotationBookContent,
        )

        assertEquals(expected, ReaderProviderRoutes.all.associate { it.path to it.requestCode })
        expected.forEach { (path, requestCode) ->
            assertEquals(requestCode, ReaderProviderRoutes.requestForPath(path))
        }
    }

    @Test
    fun `registered paths and request codes are one to one`() {
        val routes = ReaderProviderRoutes.all

        assertEquals(routes.size, routes.map { it.path }.distinct().size)
        assertEquals(routes.size, routes.map { it.requestCode }.distinct().size)
        routes.forEach { route ->
            assertEquals(
                route.requestCode,
                ReaderProviderRoutes.requestForMatcherCode(route.requestCode.ordinal),
            )
        }
    }

    @Test
    fun `rss delete dispatches only to rss controller`() {
        var bookSelection: String? = null
        var rssSelection: String? = null

        dispatchReaderProviderDelete(
            ReaderProviderRequestCode.DeleteRssSources,
            "rss-payload",
            deleteBookSources = { bookSelection = it },
            deleteRssSources = { rssSelection = it },
        )

        assertNull(bookSelection)
        assertEquals("rss-payload", rssSelection)
    }

    @Test
    fun `book delete dispatch remains unchanged`() {
        var bookSelection: String? = null
        var rssSelection: String? = null

        dispatchReaderProviderDelete(
            ReaderProviderRequestCode.DeleteBookSources,
            "book-payload",
            deleteBookSources = { bookSelection = it },
            deleteRssSources = { rssSelection = it },
        )

        assertEquals("book-payload", bookSelection)
        assertNull(rssSelection)
    }

    @Test
    fun `rss insert dispatches only to rss handlers`() = runBlocking {
        val calls = mutableListOf<String>()

        dispatchReaderProviderInsert(
            ReaderProviderRequestCode.SaveRssSource,
            "single-rss",
            saveRssSource = { calls.add("single:$it") },
        )
        dispatchReaderProviderInsert(
            ReaderProviderRequestCode.SaveRssSources,
            "multiple-rss",
            saveRssSources = { calls.add("multiple:$it") },
        )

        assertEquals(listOf("single:single-rss", "multiple:multiple-rss"), calls)
    }

    @Test
    fun `valid insert without values does not invoke its handler`() = runBlocking {
        var called = false

        dispatchReaderProviderInsert(
            ReaderProviderRequestCode.SaveRssSource,
            null,
            valuesPresent = false,
            saveRssSource = { called = true },
        )

        assertFalse(called)
    }

    @Test
    fun `invalid insert request still fails without values`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                dispatchReaderProviderInsert(
                    ReaderProviderRequestCode.GetRssSources,
                    null,
                    valuesPresent = false,
                )
            }
        }
    }

    @Test
    fun `rss query dispatches only to rss handlers`() {
        val parameters = mapOf("url" to listOf("rss-url"))

        val source = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetRssSource,
            parameters,
            getRssSource = {
                assertEquals(parameters, it)
                "single-rss"
            },
        )
        val sources = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetRssSources,
            parameters,
            getRssSources = { "multiple-rss" },
        )

        assertEquals("single-rss", source)
        assertEquals("multiple-rss", sources)
    }

    @Test
    fun `reading queries dispatch only to safe reading handlers`() {
        val parameters = mapOf("url" to listOf("book-url"))

        val snapshot = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetReadingSnapshot,
            parameters,
            getReadingSnapshot = {
                assertEquals(parameters, it)
                "snapshot"
            },
        )
        val readableContent = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetReadableBookContent,
            parameters,
            getReadableBookContent = {
                assertEquals(parameters, it)
                "content"
            },
        )
        val annotationContent = dispatchReaderProviderQuery(
            ReaderProviderRequestCode.GetAnnotationBookContent,
            parameters,
            getAnnotationBookContent = {
                assertEquals(parameters, it)
                "annotation-content"
            },
        )

        assertEquals("snapshot", snapshot)
        assertEquals("content", readableContent)
        assertEquals("annotation-content", annotationContent)
    }
}
