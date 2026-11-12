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
        try {
            val prefs = applicationContext.getSharedPreferences("sfl", Context.MODE_PRIVATE)
            val source = prefs.getString("source", "p2p") ?: "p2p"
            val conn = (URL("https://sfl.world/api/v1/prices").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: root
            val market = data.optJSONObject(source) ?: findObject(data, source) ?: JSONObject()
            val now = System.currentTimeMillis()
            val editor = prefs.edit().putLong("updated", now).remove("last_error")
            listOf("Pepper", "Blueberry").forEach { name ->
                val price = toPrice(market.opt(name) ?: findValue(market, name))
                if (price != null) {
                    editor.putString("${name}_price", price.toString())
                    val history = JSONArray(prefs.getString("${name}_history", "[]"))
                    val last = if (history.length() > 0) history.optJSONObject(history.length()-1) else null
                    if (last == null || last.optDouble("p") != price || now - last.optLong("t") > 14*60*1000) {
                        history.put(JSONObject().put("t", now).put("p", price))
                    }
                    while (history.length() > 300) history.remove(0)
                    editor.putString("${name}_history", history.toString())
                    checkAlarm(prefs, name, price)
                }
            }
            editor.apply()
            SflWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            applicationContext.getSharedPreferences("sfl", Context.MODE_PRIVATE).edit()
                .putString("last_error", e.message ?: "Error de conexión").apply()
            SflWidgetProvider.updateAll(applicationContext)
            Result.retry()
        }
    }

    private fun checkAlarm(prefs: android.content.SharedPreferences, name: String, price: Double) {
        val buy = prefs.getString("${name}_buy", "")?.toDoubleOrNull()
        val sell = prefs.getString("${name}_sell", "")?.toDoubleOrNull()
        val kind = when { buy != null && price <= buy -> "buy"; sell != null && price >= sell -> "sell"; else -> "" }
        val old = prefs.getString("${name}_alarm", "") ?: ""
        if (kind.isNotEmpty() && kind != old) SflWidgetProvider.notifyPrice(applicationContext, name, kind, price)
        prefs.edit().putString("${name}_alarm", kind).apply()
    }

    private fun findObject(o: JSONObject, key: String): JSONObject? {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next(); val v = o.opt(k)
            if (k.equals(key, true) && v is JSONObject) return v
            if (v is JSONObject) findObject(v, key)?.let { return it }
        }
        return null
    }
    private fun findValue(o: JSONObject, key: String): Any? {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next(); val v = o.opt(k)
            if (k.equals(key, true)) return v
            if (v is JSONObject) findValue(v, key)?.let { return it }
        }
        return null
    }
    private fun toPrice(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        is JSONObject -> listOf("price","floor","value","min").firstNotNullOfOrNull {
            val n = v.opt(it); (n as? Number)?.toDouble()
        }
        else -> null
    }
}
