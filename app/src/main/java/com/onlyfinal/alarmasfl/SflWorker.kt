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

/** Android implementation of the working HTML getJSON()/load() flow. */
class SflWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("sfl", Context.MODE_PRIVATE)
        try {
            val source = prefs.getString("source", "p2p") ?: "p2p"
            val json = getJson(prefs)
            val root = json.optJSONObject("data") ?: json
            val market = (findObject(root, source) ?: if (root.has(source)) root.optJSONObject(source) else null)
                ?: throw IllegalStateException("No se encontró el mercado ${source.uppercase()} en la respuesta")

            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            var found = 0
            listOf("Pepper", "Blueberry").forEach { name ->
                val raw = market.opt(name) ?: findValue(market, name)
                val price = toPrice(raw)
                if (price != null && price.isFinite() && price >= 0.0) {
                    found++
                    editor.putString("${name}_price", price.toString())
                    val history = JSONArray(prefs.getString("${name}_history", "[]") ?: "[]")
                    val last = if (history.length() > 0) history.optJSONObject(history.length() - 1) else null
                    if (last == null || last.optDouble("p", Double.NaN) != price || now - last.optLong("t") > 14 * 60 * 1000L) {
                        history.put(JSONObject().put("t", now).put("p", price))
                    }
                    while (history.length() > 3000) history.remove(0)
                    editor.putString("${name}_history", history.toString())
                    checkAlarm(prefs, name, price)
                }
            }
            if (found == 0) {
                throw IllegalStateException("No encontré Pepper ni Blueberry en el mercado ${source.uppercase()}. Respuesta: ${json.toString().take(400)}")
            }
            // Only mark the fetch as successful after at least one valid price was parsed.
            editor.putLong("updated", now).remove("last_error").apply()
            SflWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            prefs.edit().putString("last_error", e.message ?: "Error de conexión").apply()
            SflWidgetProvider.updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /** Direct request first; proxies are fallbacks, matching the HTML's route strategy. */
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
                    connectTimeout = 12000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "AlarmaSFL-Android/1.0")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONObject(body)
                prefs.edit().putInt("route", index).apply()
                return parsed
            } catch (e: Exception) {
                errors += "ruta $index: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }
        }
        throw IllegalStateException(errors.joinToString(" | "))
    }

    private fun checkAlarm(prefs: android.content.SharedPreferences, name: String, price: Double) {
        val buy = prefs.getString("${name}_buy", "")?.toDoubleOrNull()
        val sell = prefs.getString("${name}_sell", "")?.toDoubleOrNull()
        val kind = when {
            buy != null && price <= buy -> "buy"
            sell != null && price >= sell -> "sell"
            else -> ""
        }
        val old = prefs.getString("${name}_alarm", "") ?: ""
        if (kind.isNotEmpty() && kind != old) SflWidgetProvider.notifyPrice(applicationContext, name, kind, price)
        prefs.edit().putString("${name}_alarm", kind).apply()
    }

    private fun findObject(value: Any?, key: String, depth: Int = 0): JSONObject? {
        if (depth > 6) return null
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next(); val child = value.opt(k)
                    if (k.equals(key, true) && child is JSONObject) return child
                }
                val keys2 = value.keys()
                while (keys2.hasNext()) findObject(value.opt(keys2.next()), key, depth + 1)?.let { return it }
            }
            is JSONArray -> for (i in 0 until value.length()) findObject(value.opt(i), key, depth + 1)?.let { return it }
        }
        return null
    }

    private fun findValue(value: Any?, key: String, depth: Int = 0): Any? {
        if (depth > 6) return null
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next(); if (k.equals(key, true)) return value.opt(k)
                }
                val keys2 = value.keys()
                while (keys2.hasNext()) findValue(value.opt(keys2.next()), key, depth + 1)?.let { return it }
            }
            is JSONArray -> for (i in 0 until value.length()) findValue(value.opt(i), key, depth + 1)?.let { return it }
        }
        return null
    }

    private fun toPrice(value: Any?, depth: Int = 0): Double? {
        if (depth > 4) return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            is JSONObject -> {
                for (key in listOf("price", "floor", "value", "min")) {
                    val candidate = toPrice(value.opt(key), depth + 1)
                    if (candidate != null) return candidate
                }
                null
            }
            else -> null
        }
    }
}
