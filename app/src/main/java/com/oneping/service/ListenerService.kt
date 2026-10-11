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
 *   re-posts a silent copy via [MirrorNotifier],
 * - seeds the engine with notifications that already exist,
 * - resets cooldowns when the user opens/dismisses notifications.
 */
class ListenerService : NotificationListenerService() {

    private lateinit var mirror: MirrorNotifier

    override fun onCreate() {
        super.onCreate()
        mirror = MirrorNotifier(this)
    }

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
            val importance = rankingMap?.let { map ->
                Ranking().takeIf { map.getRanking(sbn.key, it) }?.importance
            } ?: NotificationManager.IMPORTANCE_DEFAULT
            val audible = importance >= NotificationManager.IMPORTANCE_DEFAULT

            val snapshot = NotificationParser.parse(sbn, packageName) ?: return
            val canSilence = mirror.canPost()
            val note = if (canSilence) "" else "can't post copies, NOT silenced"
            val verdict = engine.decide(snapshot, audible, note)
            when {
                verdict == CooldownEngine.Verdict.SILENCE && canSilence -> {
                    cancelNotification(sbn.key) // stops the sound/vibration already in progress
                    mirror.show(sbn, snapshot)
                }
                verdict == CooldownEngine.Verdict.PASS -> mirror.remove(sbn.key) // the original is visible again
            }
        } catch (t: Throwable) {
            Log.w(OnePingApp.TAG, "Listener failed on ${sbn.key}", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        val engine = OnePingApp.engine(this)
        val settings = engine.settings
        if (!settings.resetOnUserDismiss) return
        val why = when (reason) {
            REASON_CLICK -> "notification opened"
            REASON_CANCEL, REASON_CANCEL_ALL -> "notification dismissed"
            // For our own copies APP_CANCEL means *we* removed it, which says nothing about the user.
            REASON_APP_CANCEL -> "app cleared it (read)"
                .takeIf { settings.resetOnAppCancel && sbn.packageName != packageName }
            else -> null // includes REASON_LISTENER_CANCEL: our own fallback cancels
        } ?: return
        // Our silent copies: the user opened/dismissed them, so reset the original source.
        val snapshot = if (sbn.packageName == packageName) {
            mirror.sourceOf(sbn)
        } else {
            NotificationParser.parse(sbn, packageName)
        } ?: return
        engine.reset(snapshot, why)
    }
}
