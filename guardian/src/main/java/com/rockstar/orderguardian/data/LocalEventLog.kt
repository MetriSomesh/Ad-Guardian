package com.rockstar.orderguardian.data

import android.content.Context
import com.rockstar.orderguardian.shared.BlockedEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * The guardian's on-device log of what it stopped. Real events only — entries
 * are written by the accessibility service at the moment something is
 * ejected or skipped. Capped at the newest 50.
 */
class LocalEventLog(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun add(event: BlockedEvent) {
        val all = load().toMutableList()
        all.add(event)
        val array = JSONArray()
        for (e in all.sortedBy { it.timestampMillis }.takeLast(MAX_EVENTS)) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("timestampMillis", e.timestampMillis)
                    .put("packageName", e.packageName)
                    .put("kind", e.kind.name)
                    .put("detail", e.detail),
            )
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    @Synchronized
    fun list(): List<BlockedEvent> = load().sortedByDescending { it.timestampMillis }

    private fun load(): List<BlockedEvent> {
        val raw = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val out = ArrayList<BlockedEvent>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            out.add(
                BlockedEvent(
                    id = o.getString("id"),
                    timestampMillis = o.getLong("timestampMillis"),
                    packageName = o.getString("packageName"),
                    kind = BlockedEvent.Kind.valueOf(o.getString("kind")),
                    detail = o.getString("detail"),
                ),
            )
        }
        return out
    }

    companion object {
        private const val PREFS_NAME = "blocked_events_local"
        private const val KEY_EVENTS = "events_json"
        private const val MAX_EVENTS = 50
    }
}
