package com.victor.familytree.data

import org.json.JSONArray
import org.json.JSONObject

/* Tolerant JSON helpers: any field may be missing, null, a number, a string, a list or an object. */

internal fun JSONObject.str(vararg keys: String): String? {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        when (val v = get(k)) {
            is String -> if (v.isNotBlank()) return v.trim()
            is Number, is Boolean -> return v.toString()
            is JSONObject -> v.str("name", "value", "text", "title", "date")?.let { return it }
        }
    }
    return null
}

internal fun JSONObject.obj(key: String): JSONObject? = if (has(key) && !isNull(key)) optJSONObject(key) else null

internal fun JSONObject.bool(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Boolean -> v
        is String -> v.equals("true", true) || v.equals("yes", true)
        is Number -> v.toInt() != 0
        else -> null
    }
}

internal fun JSONObject.int(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
}

/** Items of an array field, or a single value as a one-element list. */
internal fun JSONObject.items(key: String): List<Any> {
    if (!has(key) || isNull(key)) return emptyList()
    return when (val v = get(key)) {
        is JSONArray -> (0 until v.length()).mapNotNull { i -> v.opt(i)?.takeIf { it != JSONObject.NULL } }
        else -> listOf(v)
    }
}

internal fun JSONObject.strings(key: String): List<String> =
    items(key).mapNotNull { describe(it).takeIf(String::isNotBlank) }

internal fun JSONObject.objects(key: String): List<JSONObject> = items(key).filterIsInstance<JSONObject>()

internal fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

private val PREFERRED = listOf(
    "event", "type", "title", "name", "value", "description", "text", "date", "place", "location",
    "role", "rank", "unit", "branch", "occupation", "employer", "note",
)
private val HIDDEN = setOf("id", "file", "sources", "confidence", "origin", "citations", "place_id", "xref", "record_urls", "year", "date_precision")

/** Human-readable one-line text for any JSON value. */
fun describe(v: Any?): String = when (v) {
    null, JSONObject.NULL -> ""
    is String -> v.trim()
    is Number, is Boolean -> v.toString()
    is JSONArray -> (0 until v.length()).map { describe(v.opt(it)) }.filter { it.isNotBlank() }.joinToString("; ")
    is JSONObject -> {
        val keys = v.keyList().filterNot { it in HIDDEN }
        val ordered = PREFERRED.filter { it in keys } + keys.filterNot { it in PREFERRED }
        val parts = ordered.mapNotNull { k -> describe(v.opt(k)).takeIf { it.isNotBlank() } }
        val cited = v.optJSONArray("citations")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.str("title") }.distinct()
        }.orEmpty()
        val extra = listOfNotNull(
            cited.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "source: "),
            v.str("confidence")?.let { "confidence: $it" },
            v.str("origin")?.let { "from: $it" },
        )
        (parts + extra).joinToString(" · ")
    }
    else -> v.toString()
}

/** Turns "military_service" into "Military service". */
private val LABELS = mapOf(
    "pdf_name" to "Name on PDF chart",
    "living_basis" to "Living status basis",
    "immigration_naturalization" to "Immigration & naturalization",
    "other_events" to "Other events",
    "research_facts" to "Research findings",
    "familysearch_id" to "FamilySearch ID",
)

fun humanize(key: String): String = LABELS[key] ?: key.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }

/** Top-level arrays may be bare or wrapped as {"note": ..., "<name>": [...]}. */
internal fun rootArray(text: String?, wrapperKey: String): JSONArray {
    if (text.isNullOrBlank()) return JSONArray()
    val t = text.trimStart()
    if (t.startsWith("[")) return JSONArray(t)
    val o = JSONObject(t)
    o.optJSONArray(wrapperKey)?.let { return it }
    for (k in o.keyList()) o.optJSONArray(k)?.let { return it }
    return JSONArray()
}

internal fun JSONArray.objectList(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
