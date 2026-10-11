package com.oneping.engine

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** What we could read out of one notification. */
data class Snapshot(
    val pkg: String,
    /** Cooldown key, unique across apps: "<package>|<source id>". */
    val key: String,
    /** Human-readable source: person, group or title. */
    val name: String,
    val isGroup: Boolean,
    /** Number of MessagingStyle messages (0 if the notification has no MessagingStyle). */
    val messageCount: Int,
    /** Timestamp of the newest MessagingStyle message (0 if unknown). */
    val latestTimestamp: Long,
    /** The newest message was written by the phone owner (e.g. an inline reply). */
    val latestFromSelf: Boolean,
    /** Hash of the visible text, used when there is no MessagingStyle. */
    val textFingerprint: Int,
    /** Notification.when, used when there is no MessagingStyle. */
    val postedWhen: Long,
)

object NotificationParser {

    /** Categories that must never be delayed or silenced, or that aren't "messages" at all. */
    private val IGNORED_CATEGORIES = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_LOCATION_SHARING,
    )

    /** Some apps append "(3 messages)" / "(3 new messages)" to titles. */
    private val counterSuffix = Regex("""\s*\(\d+\s+[^()]*\)\s*$""")

    /**
     * Returns null for notifications OnePing never handles: group summaries, ongoing and
     * foreground-service notifications, calls/alarms/media-like categories, untitled ones.
     */
    fun parse(sbn: StatusBarNotification, ownPackage: String): Snapshot? {
        if (sbn.packageName == ownPackage) return null
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null
        if (n.category in IGNORED_CATEGORIES) return null
        if (n.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MediaStyle") == true) return null

        val extras = n.extras
        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()
        val messages = style?.messages.orEmpty()

        val conversationTitle = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: style?.conversationTitle)?.toString().clean()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().clean()

        val latest = messages.maxByOrNull { it.timestamp }
        val selfKey = style?.user?.key
        val selfName = style?.user?.name?.toString()
        fun isSelf(m: NotificationCompat.MessagingStyle.Message): Boolean {
            val p = m.person ?: return true // a null sender means "the device user"
            return (selfKey != null && p.key == selfKey) ||
                (selfKey == null && selfName != null && p.name?.toString() == selfName)
        }
        val latestIncomingName = messages.filterNot(::isSelf).maxByOrNull { it.timestamp }
            ?.person?.name?.toString().clean()

        val isGroup = !conversationTitle.isNullOrEmpty() || style?.isGroupConversation == true
        val name = conversationTitle ?: latestIncomingName ?: title ?: return null

        // A conversation shortcut id identifies a chat precisely (Telegram, WhatsApp, Signal…);
        // without it, fall back to the displayed name.
        val sourceId = n.shortcutId?.takeIf { it.isNotBlank() }?.let { "s:$it" } ?: "n:$name"

        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TEXT))
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { append('\n').append(it) }
            latest?.let { append('\n').append(it.text) }
        }

        return Snapshot(
            pkg = sbn.packageName,
            key = "${sbn.packageName}|$sourceId",
            name = name,
            isGroup = isGroup,
            messageCount = messages.size,
            latestTimestamp = latest?.timestamp ?: 0L,
            latestFromSelf = latest != null && isSelf(latest),
            textFingerprint = text.hashCode(),
            postedWhen = n.`when`,
        )
    }

    private fun String?.clean(): String? = this?.replace(counterSuffix, "")?.trim()?.takeIf { it.isNotEmpty() }
}
