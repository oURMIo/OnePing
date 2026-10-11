package com.oneping.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.oneping.R
import com.oneping.engine.Snapshot
import com.oneping.ui.AppLabels

/**
 * Fallback mode only: shows a silent copy of a notification whose original was cancelled.
 * The copy keeps the text, avatar, tap action and action buttons (including inline reply)
 * of the original, so it behaves almost like the real one.
 */
class MirrorNotifier(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID, "Silenced notifications", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Silent copies of notifications muted by the cooldown (fallback mode)"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelOn = nm.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return permission && channelOn && nm.areNotificationsEnabled()
    }

    fun show(sbn: StatusBarNotification, source: Snapshot) {
        val original = sbn.notification
        val extras = original.extras
        val appLabel = AppLabels.label(context, sbn.packageName)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_oneping)
            .setContentTitle(extras.getCharSequence(Notification.EXTRA_TITLE) ?: source.name)
            .setContentText(extras.getCharSequence(Notification.EXTRA_TEXT))
            .setSubText(appLabel)
            .setWhen(original.`when`)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(original.category)
            .setColor(original.color)
            .setContentIntent(original.contentIntent)
            .setDeleteIntent(original.deleteIntent)
            .setStyle(copyStyle(original, source))
            .addExtras(Bundle().apply {
                putString(EXTRA_SRC_PKG, source.pkg)
                putString(EXTRA_SRC_KEY, source.key)
                putString(EXTRA_SRC_NAME, source.name)
            })

        loadLargeIcon(original, sbn.packageName)?.let(builder::setLargeIcon)
        original.actions?.forEach { a ->
            // Icons of the other app can't be shown by us, so actions are text-only.
            val copy = Notification.Action.Builder(null as Icon?, a.title, a.actionIntent)
                .addExtras(a.extras)
                .setAllowGeneratedReplies(a.allowGeneratedReplies)
            a.remoteInputs?.forEach(copy::addRemoteInput)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) copy.setSemanticAction(a.semanticAction)
            builder.addAction(copy.build())
        }
        nm.notify(TAG, sbn.key.hashCode(), builder.build())
    }

    fun remove(originalKey: String) = nm.cancel(TAG, originalKey.hashCode())

    /** Recovers the source of one of our copies (for cooldown resets). */
    fun sourceOf(sbn: StatusBarNotification): Snapshot? {
        if (sbn.tag != TAG) return null
        val e = sbn.notification.extras
        val pkg = e.getString(EXTRA_SRC_PKG) ?: return null
        val key = e.getString(EXTRA_SRC_KEY) ?: return null
        return Snapshot(pkg, key, e.getString(EXTRA_SRC_NAME) ?: key, false, 0, 0, false, 0, 0)
    }

    /** Re-creates the conversation as plain text lines (other apps' Person icons can't be reused). */
    private fun copyStyle(original: Notification, source: Snapshot): Notification.Style? {
        val messages = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(original)?.messages
        }.getOrNull().orEmpty()
        if (messages.isEmpty()) {
            val big = original.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: original.extras.getCharSequence(Notification.EXTRA_TEXT)
            return big?.let { Notification.BigTextStyle().bigText(it) }
        }
        return Notification.InboxStyle().also { style ->
            messages.takeLast(MAX_LINES).forEach { m ->
                val who = m.person?.name
                style.addLine(if (source.isGroup && who != null) "$who: ${m.text}" else m.text)
            }
        }
    }

    private fun loadLargeIcon(n: Notification, pkg: String): Icon? = runCatching {
        val icon = n.getLargeIcon() ?: return null
        val pkgContext = context.createPackageContext(pkg, 0)
        val drawable = icon.loadDrawable(pkgContext) ?: return null
        Icon.createWithBitmap(drawable.toBitmap(ICON_PX, ICON_PX))
    }.getOrNull()

    private companion object {
        const val CHANNEL_ID = "silenced_copies"
        const val TAG = "oneping_mirror"
        const val EXTRA_SRC_PKG = "oneping.src_pkg"
        const val EXTRA_SRC_KEY = "oneping.src_key"
        const val EXTRA_SRC_NAME = "oneping.src_name"
        const val MAX_LINES = 6
        const val ICON_PX = 192
    }
}
