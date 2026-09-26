package com.rudra.texthistory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "text_history_prefs"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 500
        private const val MAX_TEXT_LENGTH = 2000
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TEXT_LENGTH) return

        val history = loadArray()
        val entry = JSONObject().apply {
            put("text", trimmed)
            put("timestamp", System.currentTimeMillis())
        }

        val newHistory = JSONArray()
        newHistory.put(entry)
        val limit = minOf(history.length(), MAX_ENTRIES - 1)
        for (i in 0 until limit) {
            val oldItem = history.optJSONObject(i)
            if (oldItem != null) {
                newHistory.put(oldItem)
            }
        }

        prefs.edit().putString(KEY_HISTORY, newHistory.toString()).apply()
    }

    fun getAll(): List<HistoryEntry> {
        val history = loadArray()
        val entries = mutableListOf<HistoryEntry>()
        for (i in 0 until history.length()) {
            try {
                val obj = history.getJSONObject(i)
                entries.add(
                    HistoryEntry(
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            } catch (_: Exception) {
                // skip malformed entries
            }
        }
        return entries
    }

    fun clearAll() {
        prefs.edit().putString(KEY_HISTORY, JSONArray().toString()).apply()
    }

    private fun loadArray(): JSONArray {
        val json = prefs.getString(KEY_HISTORY, null) ?: return JSONArray()
        return try {
            JSONArray(json)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    data class HistoryEntry(val text: String, val timestamp: Long)
}
