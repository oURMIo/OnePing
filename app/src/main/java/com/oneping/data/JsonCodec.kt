package com.oneping.data

import org.json.JSONArray
import org.json.JSONObject

/** Tiny org.json based (de)serializers for the collections kept in DataStore. */
internal object JsonCodec {

    fun encodeLongMap(map: Map<String, Long>): String =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()

    fun decodeLongMap(json: String?): Map<String, Long> = parse(json) { obj ->
        obj.keys().asSequence().associateWith { obj.getLong(it) }
    } ?: emptyMap()

    fun encodeBoolMap(map: Map<String, Boolean>): String =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()

    fun decodeBoolMap(json: String?): Map<String, Boolean> = parse(json) { obj ->
        obj.keys().asSequence().associateWith { obj.getBoolean(it) }
    } ?: emptyMap()

    fun encodeRules(rules: Map<String, SenderRule>): String = JSONObject().apply {
        rules.forEach { (key, rule) ->
            put(key, JSONObject().put("mode", rule.mode.name).put("cooldown", rule.customCooldownSec))
        }
    }.toString()

    fun decodeRules(json: String?): Map<String, SenderRule> = parse(json) { obj ->
        obj.keys().asSequence().associateWith { key ->
            val r = obj.getJSONObject(key)
            SenderRule(
                mode = enumOr(r.optString("mode"), RuleMode.DEFAULT),
                customCooldownSec = r.optInt("cooldown", DEFAULT_COOLDOWN_SEC),
            )
        }
    } ?: emptyMap()

    fun encodeSenders(senders: Collection<KnownSender>): String = JSONArray().apply {
        senders.forEach {
            put(
                JSONObject().put("key", it.key).put("pkg", it.pkg).put("name", it.name)
                    .put("group", it.isGroup).put("seen", it.lastSeen)
            )
        }
    }.toString()

    fun decodeSenders(json: String?): List<KnownSender> = parseArray(json) { o ->
        KnownSender(
            key = o.getString("key"),
            pkg = o.optString("pkg"),
            name = o.optString("name", o.getString("key")),
            isGroup = o.optBoolean("group"),
            lastSeen = o.optLong("seen"),
        )
    }

    fun encodeLog(events: List<LogEvent>): String = JSONArray().apply {
        events.forEach {
            put(
                JSONObject().put("t", it.time).put("p", it.pkg).put("s", it.sender)
                    .put("d", it.decision.name).put("x", it.detail)
            )
        }
    }.toString()

    fun decodeLog(json: String?): List<LogEvent> = parseArray(json) { o ->
        LogEvent(
            time = o.getLong("t"),
            pkg = o.optString("p"),
            sender = o.getString("s"),
            decision = enumOr(o.optString("d"), Decision.ALERTED),
            detail = o.optString("x"),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun <T> parse(json: String?, block: (JSONObject) -> T): T? =
        if (json.isNullOrBlank()) null else runCatching { block(JSONObject(json)) }.getOrNull()

    private fun <T> parseArray(json: String?, block: (JSONObject) -> T): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> runCatching { block(arr.getJSONObject(i)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}
