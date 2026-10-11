package com.oneping.data

const val MIN_COOLDOWN_SEC = 30
const val MAX_COOLDOWN_SEC = 60 * 60
const val DEFAULT_COOLDOWN_SEC = 5 * 60

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
    val resetOnUserDismiss: Boolean = false,
    /** Also reset when the source app removes the notification itself (e.g. the chat was read). */
    val resetOnAppCancel: Boolean = false,
    /** Whether apps the user hasn't configured yet are monitored. */
    val monitorNewApps: Boolean = true,
    /** Per-app on/off chosen by the user; overrides [monitorNewApps]. */
    val appOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun isMonitored(pkg: String): Boolean =
        appOverrides[pkg] ?: (monitorNewApps && pkg !in DEFAULT_EXCLUDED_PACKAGES)
}

enum class RuleMode(val label: String) {
    DEFAULT("Use global quiet period"),
    CUSTOM("Custom quiet period"),
    ALWAYS("Always alert (no cooldown)"),
    MUTED("Always silent"),
}

data class SenderRule(
    val mode: RuleMode = RuleMode.DEFAULT,
    val customCooldownSec: Int = DEFAULT_COOLDOWN_SEC,
)

/** A person / chat / source seen by the app. [key] is unique across apps. */
data class KnownSender(
    val key: String,
    val pkg: String,
    val name: String,
    val isGroup: Boolean,
    val lastSeen: Long,
)

enum class Decision(val label: String) {
    ALERTED("Alerted"),
    COOLDOWN("Silenced (cooldown)"),
    MUTED("Silenced (always silent)"),
    RESET("Cooldown reset"),
}

data class LogEvent(
    val time: Long,
    val pkg: String,
    val sender: String,
    val decision: Decision,
    val detail: String = "",
)
