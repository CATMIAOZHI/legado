package io.legado.app.api

import android.net.Uri
import io.legado.app.model.analyzeRule.ReviewRuleParser
import org.json.JSONObject
import splitties.init.appCtx

internal data class OperitAiReviewSummary(
    val authority: String,
    val contentHash: String,
    val counts: Map<Int, Int>,
    val previews: Map<Int, String>,
)

internal object OperitAiReviewClient {
    fun getSummary(
        bookId: String,
        chapterIndex: Int,
        contentHash: String,
    ): OperitAiReviewSummary? {
        for (authority in installedAuthorities()) {
            val data = query(
                authority = authority,
                path = "reviews/summary",
                parameters = mapOf(
                    "bookId" to bookId,
                    "chapterIndex" to chapterIndex.toString(),
                    "contentHash" to contentHash,
                ),
            ) ?: continue
            if (!data.optBoolean("enabled") || !data.optBoolean("ready")) continue
            val comments = data.optJSONArray("comments") ?: continue
            val counts = linkedMapOf<Int, Int>()
            val previews = linkedMapOf<Int, String>()
            repeat(comments.length()) { index ->
                val item = comments.optJSONObject(index) ?: return@repeat
                val paragraphIndex = item.optInt("paragraphIndex")
                val count = item.optInt("count")
                if (paragraphIndex <= 0 || count <= 0) return@repeat
                counts[paragraphIndex] = count
                item.optString("preview").trim().takeIf(String::isNotBlank)?.let {
                    previews[paragraphIndex] = it
                }
            }
            return OperitAiReviewSummary(
                authority = authority,
                contentHash = data.optString("contentHash", contentHash),
                counts = counts,
                previews = previews,
            )
        }
        return null
    }

    fun getDetail(
        authority: String,
        bookId: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        contentHash: String,
    ): List<ReviewRuleParser.DetailItem> {
        val data = query(
            authority = authority,
            path = "reviews/detail",
            parameters = mapOf(
                "bookId" to bookId,
                "chapterIndex" to chapterIndex.toString(),
                "paragraphIndex" to paragraphIndex.toString(),
                "contentHash" to contentHash,
            ),
        ) ?: return emptyList()
        if (!data.optBoolean("ready")) return emptyList()
        val comments = data.optJSONArray("comments") ?: return emptyList()
        return buildList {
            repeat(comments.length()) { index ->
                val item = comments.optJSONObject(index) ?: return@repeat
                val content = item.optString("content").trim()
                if (content.isBlank()) return@repeat
                val badges = item.optJSONArray("badges")
                add(
                    ReviewRuleParser.DetailItem(
                        id = "operit-ai-${item.optLong("id", index.toLong())}",
                        avatar = null,
                        name = item.optString("name", "AI 伴读"),
                        replyToName = null,
                        badges = buildList {
                            if (badges != null) {
                                repeat(badges.length()) { badgeIndex ->
                                    badges.optString(badgeIndex)
                                        .trim()
                                        .takeIf(String::isNotBlank)
                                        ?.let(::add)
                                }
                            }
                            if (isEmpty()) add("AI")
                        },
                        content = content,
                        imageUrl = null,
                        audioUrl = null,
                        time = null,
                        likeCount = null,
                        replyCount = null,
                        replies = emptyList(),
                    )
                )
            }
        }
    }

    private fun installedAuthorities(): List<String> =
        OPERIT_AUTHORITIES.filter { authority ->
            @Suppress("DEPRECATION")
            appCtx.packageManager.resolveContentProvider(authority, 0) != null
        }

    fun reviewRoots(): List<Uri> = installedAuthorities().map { authority ->
        Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath("reviews")
            .build()
    }

    private fun query(
        authority: String,
        path: String,
        parameters: Map<String, String>,
    ): JSONObject? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendEncodedPath(path)
            .apply {
                parameters.forEach { (name, value) -> appendQueryParameter(name, value) }
            }
            .build()
        val cursor = runCatching {
            appCtx.contentResolver.query(uri, arrayOf(RESULT_COLUMN), null, null, null)
        }.getOrNull() ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val column = it.getColumnIndex(RESULT_COLUMN)
            if (column < 0) return null
            val root = runCatching { JSONObject(it.getString(column)) }.getOrNull() ?: return null
            if (!root.optBoolean("isSuccess")) return null
            return root.optJSONObject("data")
        }
    }

    private const val RESULT_COLUMN = "result"
    private val OPERIT_AUTHORITIES = listOf(
        "com.rainy.operitry.dev.readingCompanionAnnotations",
        "com.rainy.operitry.readingCompanionAnnotations",
        "com.rainy.operitry.clone.readingCompanionAnnotations",
    )
}
