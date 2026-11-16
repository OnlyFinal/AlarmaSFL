package com.onlyfinal.alarmasfl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SflWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("sfl", Context.MODE_PRIVATE)
        try {
            val source = prefs.getString("source", "p2p") ?: "p2p"
            val json = getJson(prefs)
            val root = json.optJSONObject("data") ?: json
            val market = findObject(root, source)
                ?: root.optJSONObject(source)
                ?: throw IllegalStateException("No se encontró el mercado ${source.uppercase()}")

            val prices = linkedMapOf<String, Double>()
            collectPrices(market, prices)
            if (prices.isEmpty()) throw IllegalStateException("La API no devolvió precios reconocibles para ${source.uppercase()}")

            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            val cropNames = JSONArray()
            prices.forEach { (crop, price) ->
                cropNames.put(crop)
                editor.putString(SflData.priceKey(source, crop), price.toString())
                val historyKey = SflData.historyKey(source, crop)
                val history = try { JSONArray(prefs.getString(historyKey, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
                val last = if (history.length() > 0) history.optJSONObject(history.length() - 1) else null
                if (last == null || last.optDouble("p", Double.NaN) != price || now - last.optLong("t") > 14 * 60 * 1000L) {
                    history.put(JSONObject().put("t", now).put("p", price))
                }
                while (history.length() > 3000) history.remove(0)
                editor.putString(historyKey, history.toString())

                // Mantener compatibilidad con la configuración e historial antiguos.
                if (crop.equals("Pepper", true) || crop.equals("Blueberry", true)) {
                    editor.putString("${crop}_price", price.toString())
                    val oldHistory = JSONArray(prefs.getString("${crop}_history", "[]") ?: "[]")
                    val oldLast = if (oldHistory.length() > 0) oldHistory.optJSONObject(oldHistory.length() - 1) else null
                    if (oldLast == null || oldLast.optDouble("p", Double.NaN) != price || now - oldLast.optLong("t") > 14 * 60 * 1000L) {
                        oldHistory.put(JSONObject().put("t", now).put("p", price))
                    }
                    while (oldHistory.length() > 3000) oldHistory.remove(0)
                    editor.putString("${crop}_history", oldHistory.toString())
                }
            }
            editor.putString(SflData.cropsKey(source), cropNames.toString())
                .putLong("updated", now).remove("last_error").apply()

            checkAlarms(source, prices)
            SflWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            prefs.edit().putString("last_error", e.message ?: "Error de conexión").apply()
            SflWidgetProvider.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun checkAlarms(market: String, prices: Map<String, Double>) {
        SflData.migrateLegacyAlarms(applicationContext)
        val all = SflData.alarms(applicationContext)
        var changed = false
        for (i in 0 until all.length()) {
            val alarm = all.optJSONObject(i) ?: continue
            if (!alarm.optBoolean("enabled", true) || !alarm.optString("market", "p2p").equals(market, true)) continue
            val crop = alarm.optString("crop")
            val price = prices.entries.firstOrNull { it.key.equals(crop, true) }?.value ?: continue
            val target = alarm.optDouble("target", Double.NaN)
            if (!target.isFinite()) continue
            val buy = alarm.optString("kind") == "buy"
            val reached = if (buy) price <= target else price >= target
            val armed = alarm.optBoolean("armed", true)
            if (reached && armed) {
                SflWidgetProvider.notifyPrice(applicationContext, crop, if (buy) "buy" else "sell", price, alarm.optString("id"))
                alarm.put("armed", false)
                changed = true
            } else if (!reached && !armed) {
                alarm.put("armed", true)
                changed = true
            }
        }
        if (changed) SflData.saveAlarms(applicationContext, all)
    }

    private fun getJson(prefs: android.content.SharedPreferences): JSONObject {
        val api = "https://sfl.world/api/v1/prices"
        val routes: List<(String) -> String> = listOf(
            { it },
            { "https://corsproxy.io/?url=" + java.net.URLEncoder.encode(it, "UTF-8") },
            { "https://api.allorigins.win/raw?url=" + java.net.URLEncoder.encode(it, "UTF-8") },
            { "https://api.codetabs.com/v1/proxy/?quest=" + java.net.URLEncoder.encode(it, "UTF-8") }
        )
        val saved = prefs.getInt("route", 0).coerceIn(0, routes.lastIndex)
        val order = listOf(saved) + routes.indices.filter { it != saved }
        val errors = mutableListOf<String>()
        for (index in order) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(routes[index](api)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 12000; requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "AlarmaSFL-Android/1.0")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val parsed = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                prefs.edit().putInt("route", index).apply()
                return parsed
            } catch (e: Exception) {
                errors += "ruta $index: ${e.message ?: e.javaClass.simpleName}"
            } finally { connection?.disconnect() }
        }
        throw IllegalStateException(errors.joinToString(" | "))
    }

    private fun findObject(value: Any?, key: String, depth: Int = 0): JSONObject? {
        if (depth > 7) return null
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.equals(key, true)) value.optJSONObject(k)?.let { return it }
                }
                val nested = value.keys()
                while (nested.hasNext()) findObject(value.opt(nested.next()), key, depth + 1)?.let { return it }
            }
            is JSONArray -> for (i in 0 until value.length()) findObject(value.opt(i), key, depth + 1)?.let { return it }
        }
        return null
    }

    private fun collectPrices(value: Any?, out: MutableMap<String, Double>, depth: Int = 0) {
        if (depth > 8) return
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    val price = toPrice(child)
                    if (price != null && price.isFinite() && price >= 0.0 &&
                        key.lowercase() !in setOf("price", "floor", "value", "min", "max", "updated", "timestamp")) {
                        out[key] = price
                    } else if (child is JSONObject || child is JSONArray) {
                        collectPrices(child, out, depth + 1)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectPrices(value.opt(i), out, depth + 1)
        }
    }

    private fun toPrice(value: Any?, depth: Int = 0): Double? {
        if (depth > 4) return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            is JSONObject -> {
                for (key in listOf("price", "floor", "value", "min")) {
                    toPrice(value.opt(key), depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }
}
