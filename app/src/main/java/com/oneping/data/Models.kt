package com.oneping.data

const val MIN_COOLDOWN_SEC = 30
const val MAX_COOLDOWN_SEC = 60 * 60
const val DEFAULT_COOLDOWN_SEC = 5 * 60

/** How long the tile's pause and "Mute 1 h" (notification card / People) last. */
const val PAUSE_MS = 60 * 60 * 1000L

/** How long the home screen's "Silence 15 min" lasts. */
const val SILENCE_ALL_MS = 15 * 60 * 1000L

/** "Until I turn it off" for [AppSettings.pausedUntil]. */
const val FOREVER = Long.MAX_VALUE

/** The big mode switch on the home screen. */
enum class Mode { ONE_PING, EVERYONE_RINGS, SILENCE_ALL }

/** Apps that are never touched unless the user explicitly turns them on. */
val DEFAULT_EXCLUDED_PACKAGES = setOf(
    "android",
    "com.android.systemui",
    "com.android.phone",
    "com.android.server.telecom",
    "com.google.android.dialer",
    "com.google.android.deskclock",
    "com.android.deskclock",
)

data class AppSettings(
    val globalCooldownSec: Int = DEFAULT_COOLDOWN_SEC,
    /** Reset a source's timer when I open, swipe away or read its notification. */
    val resetWhenSeen: Boolean = false,
    /** Everyone rings normally (OnePing does nothing) until this time (epoch ms). 0 = off. */
    val pausedUntil: Long = 0L,
    /** Every handled notification is silent until this time (epoch ms). 0 = off. */
    val silenceAllUntil: Long = 0L,
    /** Whether apps the user hasn't configured yet are monitored. */
    val monitorNewApps: Boolean = true,
    /** Per-app on/off chosen by the user; overrides [monitorNewApps]. */
    val appOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun isMonitored(pkg: String): Boolean =
        appOverrides[pkg] ?: (monitorNewApps && pkg !in DEFAULT_EXCLUDED_PACKAGES)

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = pausedUntil > now

    fun isSilencingAll(now: Long = System.currentTimeMillis()): Boolean = silenceAllUntil > now

    fun mode(now: Long = System.currentTimeMillis()): Mode = when {
        isSilencingAll(now) -> Mode.SILENCE_ALL
        isPaused(now) -> Mode.EVERYONE_RINGS
        else -> Mode.ONE_PING
    }
}

enum class RuleMode(val label: String) {
    DEFAULT("Default quiet period"),
    CUSTOM("Own quiet period"),
    ALWAYS("Always ring"),
    MUTED("Always silent"),
}

data class SenderRule(
    val mode: RuleMode = RuleMode.DEFAULT,
    val customCooldownSec: Int = DEFAULT_COOLDOWN_SEC,
    /** Temporarily silent until this time (epoch ms), regardless of [mode]. 0 = not muted. */
    val mutedUntil: Long = 0L,
) {
    val isDefault: Boolean get() = mode == RuleMode.DEFAULT && mutedUntil == 0L
    fun isTemporarilyMuted(now: Long = System.currentTimeMillis()) = mutedUntil > now
}

/** A person / chat seen by the app. [key] is unique across apps. */
data class KnownSender(
    val key: String,
    val pkg: String,
    val name: String,
    val isGroup: Boolean,
    val lastSeen: Long,
)

enum class Decision(val label: String) {
    ALERTED("Rang"),
    COOLDOWN("Silenced"),
    MUTED("Silenced (muted)"),
    RESET("Timer reset"),
}

data class LogEvent(
    val time: Long,
    val pkg: String,
    val sender: String,
    val decision: Decision,
    val detail: String = "",
)

/** Per-source counters for the "Today" card. */
data class SourceStats(
    val key: String,
    val pkg: String,
    val name: String,
    val messages: Int,
    val rang: Int,
    val lastTime: Long,
)

/** Counters for one calendar day ([day] = yyyyMMdd). */
data class TodayStats(
    val day: Int = 0,
    val silenced: Int = 0,
    val sources: Map<String, SourceStats> = emptyMap(),
)
