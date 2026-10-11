package com.oneping.engine

import com.oneping.data.AppSettings
import com.oneping.data.Decision
import com.oneping.data.KnownSender
import com.oneping.data.LogEvent
import com.oneping.data.MAX_COOLDOWN_SEC
import com.oneping.data.Repository
import com.oneping.data.RuleMode
import com.oneping.data.SenderRule
import com.oneping.data.clampCooldown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides, per source (person/chat in a given app), whether a notification may make a sound.
 *
 * [decide] is synchronous and never does I/O: a repeat must be cancelled within milliseconds to
 * cut its sound short, so all state lives in memory and is persisted in the background.
 */
class CooldownEngine(
    private val repository: Repository,
    /** Single-threaded scope: keeps DataStore writes in order. */
    private val ioScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Verdict { PASS, SILENCE }

    /** Last state seen per source, to tell "new message" apart from "same notification updated". */
    private data class Seen(
        val messageCount: Int,
        val latestTimestamp: Long,
        val textFingerprint: Int,
        val postedWhen: Long,
    )

    @Volatile var settings = AppSettings()
        private set
    @Volatile private var rules: Map<String, SenderRule> = emptyMap()

    private val seen = HashMap<String, Seen>()
    private val lastSignals = HashMap<String, Long>()
    private val seenApps = HashSet<String>()

    /** Loads persisted state and keeps settings/rules in sync. Call once, before any [decide]. */
    suspend fun load(scope: CoroutineScope) {
        settings = repository.settings.first()
        rules = repository.rules.first()
        val signals = repository.loadLastSignals()
        val apps = repository.loadSeenApps().keys
        synchronized(this) {
            lastSignals.putAll(signals)
            seenApps.addAll(apps)
        }
        scope.launch { repository.settings.collect { settings = it } }
        scope.launch { repository.rules.collect { rules = it } }
    }

    fun isMonitored(pkg: String): Boolean = settings.isMonitored(pkg)

    /** Remember notifications that already exist so a (re)start doesn't treat them as new. */
    @Synchronized
    fun seed(s: Snapshot) {
        seen[s.key] = s.toSeen(seen[s.key])
    }

    /**
     * @param canAlert whether the notification would make noise at all (channel importance
     *   DEFAULT or higher). Quiet notifications never start a cooldown.
     * @param note extra text for the log when a notification is silenced.
     */
    @Synchronized
    fun decide(s: Snapshot, canAlert: Boolean, note: String = ""): Verdict {
        val now = clock()
        if (seenApps.add(s.pkg)) ioScope.launch { repository.markAppSeen(s.pkg, now) }
        if (!settings.isMonitored(s.pkg)) return Verdict.PASS

        val prev = seen[s.key]
        seen[s.key] = s.toSeen(prev)
        val rule = rules[s.key] ?: SenderRule()
        val cooldownMs = when (rule.mode) {
            RuleMode.CUSTOM -> rule.customCooldownSec.clampCooldown()
            else -> settings.globalCooldownSec
        } * 1000L
        val elapsed = lastSignals[s.key]?.let { now - it } ?: Long.MAX_VALUE
        val inCooldown = rule.mode != RuleMode.ALWAYS && elapsed < cooldownMs

        if (!isNewMessage(prev, s) || s.latestFromSelf) {
            // Same message re-posted / edited / own reply: never let it ring during a cooldown.
            return if (canAlert && (inCooldown || rule.mode == RuleMode.MUTED)) Verdict.SILENCE else Verdict.PASS
        }

        val sender = KnownSender(s.key, s.pkg, s.name, s.isGroup, now)
        if (!canAlert) {
            persist(sender, event = null)
            return Verdict.PASS
        }

        val (decision, detail) = when {
            rule.mode == RuleMode.MUTED -> Decision.MUTED to note
            inCooldown -> Decision.COOLDOWN to listOf("${remaining(cooldownMs - elapsed)} left", note)
                .filter { it.isNotEmpty() }.joinToString(" · ")
            else -> Decision.ALERTED to ""
        }
        if (decision == Decision.ALERTED) lastSignals[s.key] = now
        lastSignals.entries.removeAll { now - it.value > MAX_COOLDOWN_SEC * 2000L }
        persist(sender, LogEvent(now, s.pkg, s.name, decision, detail))
        return if (decision == Decision.ALERTED) Verdict.PASS else Verdict.SILENCE
    }

    /** The user looked at the notification: the next message from this source alerts right away. */
    @Synchronized
    fun reset(s: Snapshot, why: String) {
        if (lastSignals.remove(s.key) == null) return
        persist(sender = null, LogEvent(clock(), s.pkg, s.name, Decision.RESET, why))
    }

    private fun persist(sender: KnownSender?, event: LogEvent?) {
        val signals = HashMap(lastSignals)
        ioScope.launch { repository.record(signals, sender, event) }
    }

    private fun isNewMessage(prev: Seen?, s: Snapshot): Boolean {
        if (prev == null) return true
        // MessagingStyle: a newer message timestamp (or more messages at the same time) = new message.
        if (s.latestTimestamp > 0 && prev.latestTimestamp > 0) {
            return s.latestTimestamp > prev.latestTimestamp ||
                (s.latestTimestamp == prev.latestTimestamp && s.messageCount > prev.messageCount)
        }
        // No MessagingStyle: fall back to message count, text or notification time changes.
        return s.messageCount > prev.messageCount ||
            s.textFingerprint != prev.textFingerprint ||
            s.postedWhen > prev.postedWhen
    }

    private fun Snapshot.toSeen(prev: Seen?) = Seen(
        messageCount = messageCount,
        latestTimestamp = maxOf(latestTimestamp, prev?.latestTimestamp ?: 0L),
        textFingerprint = textFingerprint,
        postedWhen = maxOf(postedWhen, prev?.postedWhen ?: 0L),
    )

    private fun remaining(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"
    }
}
