package org.fossify.messages.helpers

import android.provider.Telephony.Sms
import org.fossify.messages.models.Message
import java.text.Normalizer

internal data class QuotedSmsReaction(val emoji: String, val quote: String)

internal object SmsReactions {
    private val emojiPrefix = Regex(
        """^[\u200A\u200B]*([\x{1F000}-\x{1FAFF}\u2600-\u27BF\u2764](?:[\uFE0F\x{1F3FB}-\x{1F3FF}])*)[\u200A\u200B]*[ \u00A0\u202F]+(?:à|to)[ \u00A0\u202F]+(.+)$""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val namedPrefix = Regex(
        """^(Liked|Loved|Laughed at|Emphasized|Questioned|Disliked) (.+)$""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val namedEmoji = mapOf(
        "Liked" to "👍", "Loved" to "❤️", "Laughed at" to "😂",
        "Emphasized" to "‼️", "Questioned" to "❓", "Disliked" to "👎",
    )

    fun attachToEarlierMessages(messages: List<Message>): List<Message> {
        val links = findLinks(messages)
        val reactionsByMessage = links.groupBy({ it.originalId }, { it.emoji })
        val hiddenReactions = links.mapTo(mutableSetOf()) { it.reactionId }
        return messages.mapNotNull { message ->
            when {
                message.isMMS || message.isScheduled -> message
                message.id in hiddenReactions -> null
                else -> reactionsByMessage[message.id]?.let { reactions ->
                    message.copy().apply { displayedReactions = reactions.distinct() }
                } ?: message
            }
        }
    }

    fun includeAttachedReactions(messages: List<Message>, selected: List<Message>): List<Message> {
        val originalIds = selected.filterNot { it.isMMS || it.isScheduled }.mapTo(mutableSetOf()) { it.id }
        if (originalIds.isEmpty()) return selected
        val reactionIds = findLinks(messages).filter { it.originalId in originalIds }.mapTo(mutableSetOf()) { it.reactionId }
        return (selected + messages.filter { !it.isMMS && !it.isScheduled && it.id in reactionIds })
            .distinctBy { it.getStableId() }
    }

    private data class Link(val originalId: Long, val reactionId: Long, val emoji: String)

    private fun findLinks(messages: List<Message>): List<Link> {
        val links = mutableListOf<Link>()
        val candidatesByBody = mutableMapOf<Pair<Long, String>, MutableList<Long>>()
        // SMS timestamps in the message model have second precision.
        for (message in messages.sortedWith(compareBy<Message> { it.date }.thenBy { it.id })) {
            if (message.isMMS || message.isScheduled) continue
            val received = message.isReceivedMessage()
            if (!received && message.type != Sms.MESSAGE_TYPE_SENT) continue

            val reaction = if (received) parse(message.body) else null
            val originalId = reaction?.let { candidatesByBody[message.threadId to it.quote]?.singleOrNull() }
            if (reaction != null && originalId != null) {
                links.add(Link(originalId, message.id, reaction.emoji))
            } else {
                val key = message.threadId to normalize(message.body)
                candidatesByBody.getOrPut(key, ::mutableListOf).add(message.id)
            }
        }
        return links
    }

    fun parse(body: String): QuotedSmsReaction? {
        val emojiMatch = emojiPrefix.matchEntire(body)
        if (emojiMatch != null) {
            val (emoji, quote) = emojiMatch.destructured
            return unquote(quote)?.let { QuotedSmsReaction(emoji, normalize(it)) }
        }
        val match = namedPrefix.matchEntire(body) ?: return null
        val (label, quote) = match.destructured
        return unquote(quote)?.let { QuotedSmsReaction(namedEmoji.getValue(label), normalize(it)) }
    }

    fun normalize(body: String): String =
        Normalizer.normalize(body.replace("\r\n", "\n").replace("\uFE0F", ""), Normalizer.Form.NFC)

    private fun unquote(text: String): String? {
        val quoted = text.trim('\u200A', '\u200B')
        if (quoted.length < 3) return null
        val closing = when (quoted.first()) {
            '"' -> '"'
            '“' -> '”'
            '«' -> '»'
            else -> return null
        }
        return quoted.substring(1, quoted.lastIndex).takeIf { quoted.last() == closing }
            ?.trim('\u200A', '\u200B')
            ?.let { if (closing == '»') it.trim(' ', '\u00a0', '\u202f') else it }
            ?.takeIf(String::isNotBlank)
    }
}
