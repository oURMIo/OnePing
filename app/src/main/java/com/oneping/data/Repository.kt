package com.oneping.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oneping")

/** Single source of truth for settings, per-sender rules, cooldown state, seen apps and the log. */
class Repository private constructor(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val globalCooldown = intPreferencesKey("global_cooldown_sec")
        val resetOnDismiss = booleanPreferencesKey("reset_on_dismiss")
        val pausedUntil = longPreferencesKey("paused_until")
        val silenceAllUntil = longPreferencesKey("silence_all_until")
        val stats = stringPreferencesKey("today_stats_json")
        val monitorNewApps = booleanPreferencesKey("monitor_new_apps")
        val appOverrides = stringPreferencesKey("app_overrides_json")
        val seenApps = stringPreferencesKey("seen_apps_json")
        val rules = stringPreferencesKey("rules_json")
        val senders = stringPreferencesKey("senders_json")
        val lastSignals = stringPreferencesKey("last_signals_json")
        val log = stringPreferencesKey("log_json")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            globalCooldownSec = (p[Keys.globalCooldown] ?: DEFAULT_COOLDOWN_SEC).clampCooldown(),
            resetWhenSeen = p[Keys.resetOnDismiss] ?: false,
            pausedUntil = p[Keys.pausedUntil] ?: 0L,
            silenceAllUntil = p[Keys.silenceAllUntil] ?: 0L,
            monitorNewApps = p[Keys.monitorNewApps] ?: true,
            appOverrides = JsonCodec.decodeBoolMap(p[Keys.appOverrides]),
        )
    }.distinctUntilChanged()

    val rules: Flow<Map<String, SenderRule>> =
        store.data.map { JsonCodec.decodeRules(it[Keys.rules]) }.distinctUntilChanged()

    val senders: Flow<List<KnownSender>> = store.data
        .map { p -> JsonCodec.decodeSenders(p[Keys.senders]).sortedByDescending { it.lastSeen } }
        .distinctUntilChanged()

    /** Package name → last time it posted a notification. */
    val seenApps: Flow<Map<String, Long>> =
        store.data.map { JsonCodec.decodeLongMap(it[Keys.seenApps]) }.distinctUntilChanged()

    val lastSignals: Flow<Map<String, Long>> =
        store.data.map { JsonCodec.decodeLongMap(it[Keys.lastSignals]) }.distinctUntilChanged()

    val log: Flow<List<LogEvent>> =
        store.data.map { JsonCodec.decodeLog(it[Keys.log]) }.distinctUntilChanged()

    val todayStats: Flow<TodayStats> =
        store.data.map { JsonCodec.decodeStats(it[Keys.stats]) }.distinctUntilChanged()

    // ---- settings ----

    suspend fun setGlobalCooldown(sec: Int) = store.edit { it[Keys.globalCooldown] = sec.clampCooldown() }

    suspend fun setResetWhenSeen(value: Boolean) = store.edit { it[Keys.resetOnDismiss] = value }

    /** Pause until [until] (epoch ms); 0 resumes. */
    suspend fun setPausedUntil(until: Long) = store.edit {
        it[Keys.pausedUntil] = until
        if (until > 0) it[Keys.silenceAllUntil] = 0L
    }

    /** Switches the home screen mode; the two timed modes exclude each other. */
    suspend fun setMode(mode: Mode) = store.edit {
        val now = System.currentTimeMillis()
        it[Keys.pausedUntil] = if (mode == Mode.EVERYONE_RINGS) FOREVER else 0L
        it[Keys.silenceAllUntil] = if (mode == Mode.SILENCE_ALL) now + SILENCE_ALL_MS else 0L
    }

    suspend fun setMonitorNewApps(value: Boolean) = store.edit { it[Keys.monitorNewApps] = value }

    suspend fun setAppMonitored(pkg: String, value: Boolean) = store.edit { p ->
        val map = JsonCodec.decodeBoolMap(p[Keys.appOverrides]) + (pkg to value)
        p[Keys.appOverrides] = JsonCodec.encodeBoolMap(map)
    }

    suspend fun setRule(key: String, rule: SenderRule) = store.edit { p ->
        val rules = JsonCodec.decodeRules(p[Keys.rules]).toMutableMap()
        if (rule.isDefault) rules.remove(key) else rules[key] = rule
        p[Keys.rules] = JsonCodec.encodeRules(rules)
    }

    /** Changes one source's rule in place (keeps the other fields). */
    suspend fun updateRule(key: String, change: (SenderRule) -> SenderRule) = store.edit { p ->
        val rules = JsonCodec.decodeRules(p[Keys.rules]).toMutableMap()
        val rule = change(rules[key] ?: SenderRule())
        if (rule.isDefault) rules.remove(key) else rules[key] = rule
        p[Keys.rules] = JsonCodec.encodeRules(rules)
    }

    suspend fun forgetSender(key: String) = store.edit { p ->
        p[Keys.senders] = JsonCodec.encodeSenders(JsonCodec.decodeSenders(p[Keys.senders]).filter { it.key != key })
        p[Keys.rules] = JsonCodec.encodeRules(JsonCodec.decodeRules(p[Keys.rules]) - key)
    }

    suspend fun clearLog() = store.edit { it.remove(Keys.log) }

    // ---- runtime state (written by the engine) ----

    suspend fun loadLastSignals(): Map<String, Long> = lastSignals.first()

    suspend fun loadSeenApps(): Map<String, Long> = seenApps.first()

    suspend fun loadTodayStats(): TodayStats = todayStats.first()

    /**
     * Persists one processed notification in a single DataStore transaction:
     * cooldown map, seen sender and (optionally) a log entry.
     */
    suspend fun record(
        lastSignals: Map<String, Long>,
        sender: KnownSender?,
        event: LogEvent?,
        stats: TodayStats? = null,
    ) = store.edit { p ->
        p[Keys.lastSignals] = JsonCodec.encodeLongMap(lastSignals)
        if (stats != null) p[Keys.stats] = JsonCodec.encodeStats(stats)
        if (sender != null) upsertSender(p, sender)
        if (event != null) {
            p[Keys.log] = JsonCodec.encodeLog((listOf(event) + JsonCodec.decodeLog(p[Keys.log])).take(LOG_SIZE))
        }
    }

    suspend fun markAppSeen(pkg: String, time: Long) = store.edit { p ->
        val apps = JsonCodec.decodeLongMap(p[Keys.seenApps]) + (pkg to time)
        p[Keys.seenApps] = JsonCodec.encodeLongMap(apps)
    }

    private fun upsertSender(p: MutablePreferences, sender: KnownSender) {
        val list = JsonCodec.decodeSenders(p[Keys.senders]).filter { it.key != sender.key } + sender
        p[Keys.senders] = JsonCodec.encodeSenders(list.sortedByDescending { it.lastSeen }.take(MAX_SENDERS))
    }

    companion object {
        const val LOG_SIZE = 50
        private const val MAX_SENDERS = 300

        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}

fun Int.clampCooldown(): Int = coerceIn(MIN_COOLDOWN_SEC, MAX_COOLDOWN_SEC)
