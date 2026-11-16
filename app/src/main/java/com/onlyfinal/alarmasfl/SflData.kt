package com.onlyfinal.alarmasfl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SflData {
    const val PREFS = "sfl"
    const val ALARMS_KEY = "alarms"

    fun priceKey(market: String, crop: String) = "price_${market}_${crop}"
    fun historyKey(market: String, crop: String) = "history_${market}_${crop}"
    fun cropsKey(market: String) = "crops_${market}"

    fun alarms(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try { JSONArray(prefs.getString(ALARMS_KEY, "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }
    }

    fun saveAlarms(context: Context, alarms: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ALARMS_KEY, alarms.toString()).apply()
    }

    fun migrateLegacyAlarms(context: Context, market: String) {\n        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        val all = alarms(context)\n        var changed = false\n        listOf("Pepper", "Blueberry").forEach { crop ->\n            listOf("buy", "sell").forEach { kind ->\n                val target = prefs.getString("${crop}_${kind}", "")?.toDoubleOrNull() ?: return@forEach\n                val exists = (0 until all.length()).any { i ->\n                    val a = all.optJSONObject(i)\n                    a != null && a.optString("crop").equals(crop, true) && a.optString("market") == market && a.optString("kind") == kind && a.optDouble("target") == target\n                }\n                if (!exists) {\n                    all.put(JSONObject().put("id", java.util.UUID.randomUUID().toString())\n                        .put("crop", crop).put("market", market).put("kind", kind)\n                        .put("target", target).put("enabled", true).put("armed", true))\n                    changed = true\n                }\n            }\n        }\n        if (changed) saveAlarms(context, all)\n    }\n\n    fun addAlarm(context: Context, crop: String, market: String, kind: String, target: Double): String {
        val id = java.util.UUID.randomUUID().toString()
        val all = alarms(context)
        all.put(JSONObject()
            .put("id", id).put("crop", crop).put("market", market)
            .put("kind", kind).put("target", target).put("enabled", true).put("armed", true))
        saveAlarms(context, all)
        return id
    }
}
