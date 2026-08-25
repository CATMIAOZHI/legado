package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeReviewProviderSourceTest {

    @Test
    fun `native and AI summary providers stay scoped to their chapter`() {
        val source = projectFile(
            "src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt"
        ).readText().normalizeLines()
        val applyBlock = source.substringAfter("private fun applyCombinedReviewProviders(")
            .substringBefore("private fun prefetchAdjacentReviewSummary(")

        assertTrue(applyBlock.contains("if (targetChapterIndex != chapterIndex)"))
        assertTrue(applyBlock.contains("(nativeSummary?.counts?.get(reviewId) ?: 0) +"))
        assertTrue(applyBlock.contains("(aiSummary?.counts?.get(reviewId) ?: 0)"))
        assertTrue(applyBlock.contains("nativeSummary?.keys?.get(reviewId)"))
        assertTrue(applyBlock.contains("aiSummary?.previews?.get(reviewId)"))
    }

    @Test
    fun `rendering and clicks use the same title offset`() {
        val provider = projectFile(
            "src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt"
        ).readText().normalizeLines()
        val contentView = projectFile(
            "src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt"
        ).readText().normalizeLines()
        val layout = projectFile(
            "src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt"
        ).readText().normalizeLines()
        val refreshBlock = provider.substringAfter(
            "private fun refreshReviewColumns(textChapter: TextChapter?)"
        ).substringBefore("fun getReviewCount(")

        assertTrue(refreshBlock.contains("val chapterIndex = textChapter.chapter.index"))
        assertTrue(refreshBlock.contains("chapterIndex = chapterIndex"))
        assertTrue(provider.contains("titleOffset = line.reviewTitleOffset"))
        assertTrue(provider.contains("val reviewId = paragraphNum - titleOffset"))
        assertTrue(contentView.contains("textLine.paragraphNum - textLine.reviewTitleOffset"))
        assertTrue(
            Regex(
                """callBack\.onReviewClick\(\s*resolveReviewId\(textLine\),\s*""" +
                    """column\.count,\s*textPage\.chapterIndex\s*\)"""
            ).containsMatchIn(contentView)
        )
        assertTrue(
            contentView.contains(
                "fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int)"
            )
        )
        assertTrue(
            layout.contains(
                "titleMode != 2 || bookChapter.isVolume || !textChapter.hasBodyContent"
            )
        )
        assertTrue(layout.contains("reviewTitleOffset = reviewTitleOffset"))
    }

    @Test
    fun `drawing overflow remains inside the screen clip`() {
        val provider = projectFile(
            "src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt"
        ).readText().normalizeLines()
        val layoutBlock = provider.substringAfter("fun upLayout()")
            .substringBefore("private fun setFallbackLayout()")
        val visibleRectBlock = layoutBlock.substringAfter("visibleRect.set(")

        assertTrue(visibleRectBlock.contains("viewWidth.toFloat(),"))
        assertTrue(visibleRectBlock.contains("visibleBottom.toFloat() + 10f.dpToPx()"))
    }

    private fun String.normalizeLines(): String = replace("\r\n", "\n")

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull { it.isFile }
            ?: error("Missing project file: $pathInApp")
    }
}
