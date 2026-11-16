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

    // Migra los límites antiguos a ambos mercados antes de retirar las claves antiguas.
    fun migrateLegacyAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = alarms(context)
        val cleanup = prefs.edit()
        var changed = false
        listOf("Pepper", "Blueberry").forEach { crop ->
            listOf("buy", "sell").forEach { kind ->
                val key = "${crop}_${kind}"
                val target = prefs.getString(key, "")?.toDoubleOrNull()
                if (target != null) {
                    listOf("p2p", "seq").forEach { market ->
                        val exists = (0 until all.length()).any { i ->
                            val a = all.optJSONObject(i)
                            a != null && a.optString("crop").equals(crop, true) &&
                                a.optString("market") == market && a.optString("kind") == kind &&
                                a.optDouble("target") == target
                        }
                        if (!exists) {
                            all.put(JSONObject().put("id", java.util.UUID.randomUUID().toString())
                                .put("crop", crop).put("market", market).put("kind", kind)
                                .put("target", target).put("enabled", true).put("armed", true))
                            changed = true
                        }
                    }
                    cleanup.remove(key)
                }
            }
        }
        cleanup.apply()
        if (changed) saveAlarms(context, all)
    }

    fun addAlarm(context: Context, crop: String, market: String, kind: String, target: Double): String {
        val id = java.util.UUID.randomUUID().toString()
        val all = alarms(context)
        all.put(JSONObject().put("id", id).put("crop", crop).put("market", market)
            .put("kind", kind).put("target", target).put("enabled", true).put("armed", true))
        saveAlarms(context, all)
        return id
    }
}
