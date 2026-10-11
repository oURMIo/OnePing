package com.oneping.service

import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.oneping.OnePingApp
import com.oneping.engine.CooldownEngine
import com.oneping.engine.NotificationParser

/**
 * The heart of OnePing. It:
 * - judges every new notification with the [CooldownEngine],
 * - silences repeats: cancels the original (the system stops its sound immediately) and
 *   shows the source's silent conversation card via [SilentCards],
 * - seeds the engine with notifications that already exist,
 * - forgets / resets a source when the user has seen it.
 */
class ListenerService : NotificationListenerService() {

    private val cards get() = OnePingApp.cards(this)

    override fun onListenerConnected() {
        OnePingApp.listenerConnected.value = true
        val engine = OnePingApp.engine(this)
        runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
            .mapNotNull { NotificationParser.parse(it, packageName) }
            .forEach(engine::seed)
    }

    override fun onListenerDisconnected() {
        OnePingApp.listenerConnected.value = false
    }

    override fun onDestroy() {
        OnePingApp.listenerConnected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try {
            val engine = OnePingApp.engine(this)
            val snapshot = NotificationParser.parse(sbn, packageName) ?: return
            val importance = rankingMap?.let { map ->
                Ranking().takeIf { map.getRanking(sbn.key, it) }?.importance
            } ?: NotificationManager.IMPORTANCE_DEFAULT
            val canSilence = cards.canPost()
            val note = if (canSilence) "" else "can't show cards, NOT silenced"
            val verdict = engine.decide(snapshot, importance >= NotificationManager.IMPORTANCE_DEFAULT, note)
            if (engine.isMonitored(snapshot.pkg)) cards.record(snapshot)

            when {
                verdict == CooldownEngine.Verdict.SILENCE && canSilence -> {
                    // Cancel every original of this source (stops the sound at once); the card
                    // shows the whole conversation instead.
                    activeNotifications.orEmpty()
                        .filter { it.packageName == sbn.packageName }
                        .filter { NotificationParser.parse(it, packageName)?.key == snapshot.key }
                        .forEach { cancelNotification(it.key) }
                    cards.show(sbn, snapshot)
                }
                verdict == CooldownEngine.Verdict.PASS -> cards.hide(snapshot.key) // the original is visible
            }
        } catch (t: Throwable) {
            Log.w(OnePingApp.TAG, "Listener failed on ${sbn.key}", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        val own = sbn.packageName == packageName
        val seen = when (reason) {
            REASON_CLICK -> "opened"
            REASON_CANCEL, REASON_CANCEL_ALL -> "dismissed"
            // The app cleared its own notification (chat read). For our cards it means *we* hid it.
            REASON_APP_CANCEL -> "read".takeIf { !own }
            else -> null // includes REASON_LISTENER_CANCEL: our own cancellations
        } ?: return

        val (key, pkg, name) = if (own) {
            cards.sourceOf(sbn) ?: return
        } else {
            val s = NotificationParser.parse(sbn, packageName) ?: return
            SilentCards.Source(s.key, s.pkg, s.name)
        }
        cards.forget(key)
        val engine = OnePingApp.engine(this)
        if (engine.settings.resetWhenSeen) engine.reset(key, pkg, name, seen)
    }
}
