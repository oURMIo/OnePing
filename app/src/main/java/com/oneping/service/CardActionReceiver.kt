package com.oneping.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oneping.OnePingApp
import com.oneping.data.PAUSE_MS
import com.oneping.data.Repository
import com.oneping.data.RuleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Mute 1 h" / "Always ring" buttons on a silent card. */
class CardActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(SilentCards.EXTRA_KEY) ?: return
        val pkg = intent.getStringExtra(SilentCards.EXTRA_PKG).orEmpty()
        val name = intent.getStringExtra(SilentCards.EXTRA_NAME).orEmpty()
        val repo = Repository.get(context)
        val cards = OnePingApp.cards(context)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MUTE_HOUR -> {
                        val until = System.currentTimeMillis() + PAUSE_MS
                        repo.updateRule(key) { it.copy(mutedUntil = until) }
                        cards.refresh(key, "Muted for 1 hour")
                    }
                    ACTION_ALWAYS_RING -> {
                        repo.updateRule(key) { it.copy(mode = RuleMode.ALWAYS, mutedUntil = 0L) }
                        OnePingApp.engine(context).reset(key, pkg, name, "set to always ring")
                        cards.refresh(key, "Will always ring")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MUTE_HOUR = "com.oneping.action.MUTE_HOUR"
        const val ACTION_ALWAYS_RING = "com.oneping.action.ALWAYS_RING"
    }
}
