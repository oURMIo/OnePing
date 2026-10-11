package com.oneping.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.oneping.R
import com.oneping.engine.ParsedMessage
import com.oneping.engine.Snapshot
import com.oneping.ui.AppLabels

/**
 * The silent "conversation card" shown in place of silenced notifications.
 *
 * One card per source (person/chat in an app). It keeps its own message history, so pulling the
 * card down shows the whole burst, even when the source app only shows its last few messages.
 * Cards of the same app are grouped together in the shade.
 */
class SilentCards(private val context: Context) {

    /** Source identity (what the action buttons and resets need). */
    data class Source(val key: String, val pkg: String, val name: String)

    private class Card(
        val source: Source,
        var isGroup: Boolean,
        val messages: ArrayList<ParsedMessage> = ArrayList(),
        var silenced: Int = 0,
        var status: String? = null,
        var contentIntent: PendingIntent? = null,
        var replyAction: Notification.Action? = null,
        var avatar: Icon? = null,
        var color: Int = 0,
    )

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val cards = HashMap<String, Card>()

    init {
        val channel = NotificationChannel(
            CHANNEL_ID, "Silenced messages", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Silent cards that collect messages muted by the cooldown"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
        nm.deleteNotificationChannel("silenced_copies") // channel of older versions
    }

    fun canPost(): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelOn = nm.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return permission && channelOn && nm.areNotificationsEnabled()
    }

    /** Adds the messages of a (monitored) notification to its source's history. */
    @Synchronized
    fun record(s: Snapshot) {
        val card = cards.getOrPut(s.key) { Card(Source(s.key, s.pkg, s.name), s.isGroup) }
        card.isGroup = s.isGroup
        val known = card.messages.mapTo(HashSet()) { it.time to it.text }
        s.messages.filter { (it.time to it.text) !in known }.forEach(card.messages::add)
        card.messages.sortBy { it.time }
        while (card.messages.size > MAX_MESSAGES) card.messages.removeAt(0)
    }

    /** A notification from this source was silenced: (re)post its card. */
    @Synchronized
    fun show(sbn: StatusBarNotification, s: Snapshot) {
        record(s)
        val card = cards.getValue(s.key)
        val n = sbn.notification
        card.silenced++
        card.status = null
        n.contentIntent?.let { card.contentIntent = it }
        n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }?.let { card.replyAction = it }
        loadAvatar(n, s.pkg)?.let { card.avatar = it }
        if (n.color != 0) card.color = n.color
        post(card)
    }

    /** Re-posts a card with a status line (after an action button was used). */
    @Synchronized
    fun refresh(key: String, status: String) {
        val card = cards[key] ?: return
        card.status = status
        if (isShowing(key)) post(card)
    }

    /** Hides the card but keeps the history (the original notification is visible again). */
    @Synchronized
    fun hide(key: String) {
        val card = cards[key] ?: return
        nm.cancel(TAG, key.hashCode())
        card.silenced = 0
        updateSummary(card.source.pkg)
    }

    /** The user has seen this source: drop the card and its history. */
    @Synchronized
    fun forget(key: String) {
        val card = cards.remove(key) ?: return
        nm.cancel(TAG, key.hashCode())
        updateSummary(card.source.pkg)
    }

    /** Identifies the source of one of our cards (for resets when it is opened/dismissed). */
    fun sourceOf(sbn: StatusBarNotification): Source? {
        if (sbn.tag != TAG) return null
        val e = sbn.notification.extras
        return Source(
            key = e.getString(EXTRA_KEY) ?: return null,
            pkg = e.getString(EXTRA_PKG) ?: return null,
            name = e.getString(EXTRA_NAME).orEmpty(),
        )
    }

    private fun post(card: Card) {
        val src = card.source
        val appLabel = AppLabels.label(context, src.pkg)
        val me = Person.Builder().setName("You").build()
        val them = Person.Builder().setName(src.name).apply { card.avatar?.let(::setIcon) }.build()

        // Only render as a group chat when other people than the chat's name actually write in it.
        val group = card.isGroup && card.messages.any { it.sender != null && it.sender != src.name }
        val style = Notification.MessagingStyle(me)
        if (group) style.setConversationTitle(src.name).setGroupConversation(true)
        card.messages.forEach { m ->
            val sender = when {
                m.sender == null -> null // the device owner
                group -> Person.Builder().setName(m.sender).build()
                else -> them
            }
            style.addMessage(Notification.MessagingStyle.Message(m.text, m.time, sender))
        }

        val incoming = card.messages.count { it.sender != null }
        val summary = card.status ?: "$appLabel · ${plural(incoming, "message")}, ${card.silenced} silenced"
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_oneping)
            .setContentTitle(src.name)
            .setContentText(card.messages.lastOrNull()?.text)
            .setSubText(summary)
            .setStyle(style)
            .setShowWhen(true)
            .setWhen(card.messages.lastOrNull()?.time ?: System.currentTimeMillis())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(groupKey(src.pkg))
            .setContentIntent(card.contentIntent)
            .addExtras(Bundle().apply {
                putString(EXTRA_KEY, src.key)
                putString(EXTRA_PKG, src.pkg)
                putString(EXTRA_NAME, src.name)
            })
        if (card.color != 0) builder.setColor(card.color)
        card.avatar?.let(builder::setLargeIcon)

        card.replyAction?.let { a ->
            // The other app's icons can't be used by us, so the button is text-only.
            val reply = Notification.Action.Builder(null as Icon?, a.title, a.actionIntent)
                .addExtras(a.extras)
                .setAllowGeneratedReplies(a.allowGeneratedReplies)
            a.remoteInputs?.forEach(reply::addRemoteInput)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) reply.setSemanticAction(a.semanticAction)
            builder.addAction(reply.build())
        }
        builder.addAction(action(src, CardActionReceiver.ACTION_MUTE_HOUR, "Mute 1 h"))
        builder.addAction(action(src, CardActionReceiver.ACTION_ALWAYS_RING, "Always ring"))

        nm.notify(TAG, src.key.hashCode(), builder.build())
        updateSummary(src.pkg)
    }

    /** With 2+ cards from one app, adds a summary so the shade shows them as one expandable group. */
    private fun updateSummary(pkg: String) {
        val inGroup = cards.values.filter { it.source.pkg == pkg && isShowing(it.source.key) }
        if (inGroup.size < 2) {
            nm.cancel(SUMMARY_TAG, pkg.hashCode())
            return
        }
        val appLabel = AppLabels.label(context, pkg)
        val inbox = Notification.InboxStyle().setSummaryText(appLabel)
        inGroup.forEach { c -> inbox.addLine("${c.source.name}: ${c.messages.lastOrNull()?.text.orEmpty()}") }
        val summary = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_oneping)
            .setContentTitle("$appLabel · ${plural(inGroup.size, "chat")}")
            .setSubText(appLabel)
            .setStyle(inbox)
            .setGroup(groupKey(pkg))
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(SUMMARY_TAG, pkg.hashCode(), summary)
    }

    private fun isShowing(key: String): Boolean =
        nm.activeNotifications.any { it.tag == TAG && it.id == key.hashCode() }

    private fun action(src: Source, what: String, title: String): Notification.Action {
        val intent = Intent(context, CardActionReceiver::class.java)
            .setAction(what)
            .putExtra(EXTRA_KEY, src.key)
            .putExtra(EXTRA_PKG, src.pkg)
            .putExtra(EXTRA_NAME, src.name)
        val pi = PendingIntent.getBroadcast(
            context, (src.key + what).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null as Icon?, title, pi).build()
    }

    private fun loadAvatar(n: Notification, pkg: String): Icon? = runCatching {
        val icon = n.getLargeIcon() ?: return null
        val drawable = icon.loadDrawable(context.createPackageContext(pkg, 0)) ?: return null
        Icon.createWithBitmap(drawable.toBitmap(AVATAR_PX, AVATAR_PX))
    }.getOrNull()

    private fun groupKey(pkg: String) = "oneping.$pkg"

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    companion object {
        const val EXTRA_KEY = "oneping.src_key"
        const val EXTRA_PKG = "oneping.src_pkg"
        const val EXTRA_NAME = "oneping.src_name"
        private const val CHANNEL_ID = "silent_cards"
        private const val TAG = "oneping_card"
        private const val SUMMARY_TAG = "oneping_group"
        private const val MAX_MESSAGES = 25
        private const val AVATAR_PX = 192
    }
}
