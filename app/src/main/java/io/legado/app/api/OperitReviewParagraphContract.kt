package io.legado.app.api

import java.security.MessageDigest

internal data class OperitReviewParagraph(
    val id: Int,
    val text: String,
)

internal data class OperitReviewParagraphContract(
    val hash: String,
    val paragraphs: List<OperitReviewParagraph>,
)

/**
 * Defines the paragraph IDs shared by Legado's Review UI and Operit's AI comments.
 *
 * Special layout commands and embedded newlines are rejected instead of guessing how a rendered
 * chapter will number them. Ordinary non-blank BookContent entries map one-to-one to review IDs.
 */
internal object OperitReviewParagraphContractSupport {
    fun fromTextList(textList: List<String>): OperitReviewParagraphContract? {
        if (
            textList.any { text ->
                '\n' in text ||
                    '\r' in text ||
                    text.trim() == "[newpage]" ||
                    text.trimStart().startsWith("<usehtml>")
            }
        ) {
            return null
        }
        val paragraphs = textList
            .asSequence()
            .filter(String::isNotBlank)
            .mapIndexed { index, text ->
                OperitReviewParagraph(
                    id = index + 1,
                    text = text,
                )
            }
            .toList()
        if (paragraphs.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CONTRACT_VERSION.toByteArray(Charsets.UTF_8))
        paragraphs.forEach { paragraph ->
            digest.update(0)
            digest.update(paragraph.id.toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(paragraph.text.toByteArray(Charsets.UTF_8))
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return OperitReviewParagraphContract(hash = hash, paragraphs = paragraphs)
    }

    private const val CONTRACT_VERSION = "legado-review-paragraphs-v1"
}
