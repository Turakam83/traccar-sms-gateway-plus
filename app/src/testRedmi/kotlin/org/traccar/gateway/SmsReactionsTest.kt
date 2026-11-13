package org.traccar.gateway

import org.fossify.messages.helpers.SmsReactions
import android.provider.Telephony.Sms
import org.fossify.messages.models.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsReactionsTest {
    @Test fun `parses French emoji SMS with the full quoted body`() {
        val reaction = SmsReactions.parse("👍 à \"Texte long 😊\nSuite 🌨️\"")
        assertEquals("👍", reaction?.emoji)
        assertEquals("Texte long 😊\nSuite 🌨", reaction?.quote)
    }

    @Test fun `parses English emoji and named reaction formats`() {
        assertEquals("👍🏽", SmsReactions.parse("👍🏽 to “Message”")?.emoji)
        assertEquals("❤️", SmsReactions.parse("Loved \"Message\"")?.emoji)
    }

    @Test fun observedFrenchHeartFormatWithInvisibleDelimiters() {
        val reaction = SmsReactions.parse("\u200A\u200B\u2764\uFE0F\u200B \u00E0 \"\u200ADe rien\u200A\"\u200A")
        assertEquals("\u2764\uFE0F", reaction?.emoji)
        assertEquals("De rien", reaction?.quote)
    }

    @Test fun `leaves ordinary and incomplete texts visible`() {
        assertNull(SmsReactions.parse("👍 à demain"))
        assertNull(SmsReactions.parse("Merci 👍 à \"Message\""))
        assertNull(SmsReactions.parse("👍 à \"Message"))
        assertNull(SmsReactions.parse("👍 à \"Message\" puis autre chose"))
    }

    @Test fun attachesReactionsToBothDirectionsInTheSameConversation() {
        val inbox = message(1, "Bonjour ", Sms.MESSAGE_TYPE_INBOX)
        val sent = message(2, "Ca va", Sms.MESSAGE_TYPE_SENT)
        val reactedToSent = message(3, "\u200A\u200B\uD83D\uDC4D\u200B \u00E0 \"\u200ACa va\u200A\"\u200A", Sms.MESSAGE_TYPE_INBOX)
        val reactedToInbox = message(4, "\u200A\u200B\u2764\uFE0F\u200B \u00E0 \"\u200ABonjour \u200A\"\u200A", Sms.MESSAGE_TYPE_INBOX)

        val displayed = SmsReactions.attachToEarlierMessages(listOf(inbox, sent, reactedToSent, reactedToInbox))

        assertEquals(listOf(1L, 2L), displayed.map { it.id })
        assertEquals(listOf("\u2764\uFE0F"), displayed[0].displayedReactions)
        assertEquals(listOf("\uD83D\uDC4D"), displayed[1].displayedReactions)
    }

    @Test fun keepsReactionVisibleWhenBothDirectionsMatchOrTheMessageComesLater() {
        val sent = message(1, "Same text", Sms.MESSAGE_TYPE_SENT)
        val inbox = message(2, "Same text", Sms.MESSAGE_TYPE_INBOX)
        val reaction = message(3, "Liked \"Same text\"", Sms.MESSAGE_TYPE_INBOX)

        for (messages in listOf(listOf(sent, inbox, reaction), listOf(reaction, inbox.copy(date = 4)))) {
            assertEquals(messages, SmsReactions.attachToEarlierMessages(messages))
        }
    }

    @Test fun includesOnlyLinkedReactionsWhenRemovingAnOriginalMessage() {
        val original = message(1, "Original", Sms.MESSAGE_TYPE_INBOX)
        val reaction = message(2, "Loved \"Original\"", Sms.MESSAGE_TYPE_INBOX)
        val unrelated = message(3, "Other", Sms.MESSAGE_TYPE_INBOX)
        val reactionToOther = message(4, "Liked \"Other\"", Sms.MESSAGE_TYPE_INBOX)
        val wrongThread = message(5, "Liked \"Original\"", Sms.MESSAGE_TYPE_INBOX).copy(threadId = 2)
        val messages = listOf(original, reaction, unrelated, reactionToOther, wrongThread)

        assertEquals(listOf(original, reaction), SmsReactions.includeAttachedReactions(messages, listOf(original)))
    }

    private fun message(id: Long, body: String, type: Int) = Message(
        id = id, body = body, type = type, status = 0, participants = arrayListOf(),
        date = id.toInt(), read = true, threadId = 1L, isMMS = false, attachment = null,
        senderPhoneNumber = "", senderName = "", senderPhotoUri = "", subscriptionId = -1,
    )
}
