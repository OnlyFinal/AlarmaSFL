package com.onlyfinal.alarmasfl

import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.os.Build
import android.widget.RemoteViews
import org.json.JSONArray
import kotlin.math.max

class SflWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        try { SflWork.schedule(context) }
        catch (e: Exception) { android.util.Log.e("SflWidgetProvider", "Error al programar WorkManager", e) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, SflWidgetProvider::class.java))
                .forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences("sfl", Context.MODE_PRIVATE)
            val v = RemoteViews(context.packageName, R.layout.widget_sfl)
            val market = p.getString("source", "p2p") ?: "p2p"
            v.setTextViewText(R.id.market, if (market == "seq") "Sequence" else "Plaza (P2P)")
            val updated = p.getLong("updated", 0L)
            v.setTextViewText(R.id.updated, if (updated == 0L) "sin datos" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(updated)))
            val crop1 = p.getString("widget_crop_1", "Pepper") ?: "Pepper"
            val crop2 = p.getString("widget_crop_2", "Blueberry") ?: "Blueberry"
            val mode = p.getString("widget_mode", "graph") ?: "graph"
            setItem(context, v, p, market, crop1, mode, R.id.pepper_name, R.id.pepper_price, R.id.pepper_chart, R.id.pepper_status, Color.rgb(255,90,74), 1)
            setItem(context, v, p, market, crop2, mode, R.id.blueberry_name, R.id.blueberry_price, R.id.blueberry_chart, R.id.blueberry_status, Color.rgb(108,140,255), 2)
            v.setTextViewText(R.id.widget_error, p.getString("last_error", "") ?: "")
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, v)
        }

        private fun setItem(context: Context, v: RemoteViews, p: android.content.SharedPreferences,
                            market: String, name: String, mode: String, nameId: Int, priceId: Int,
                            chartId: Int, statusId: Int, color: Int, slot: Int) {
            v.setTextViewText(nameId, name.uppercase())
            val key = SflData.priceKey(market, name)
            val price = p.getString(key, null)?.toDoubleOrNull()
                ?: p.getString("${name}_price", null)?.toDoubleOrNull()
            v.setTextViewText(priceId, price?.let { fmt(it) } ?: "--")
            val historyRaw = p.getString(SflData.historyKey(market, name), null)
                ?: p.getString("${name}_history", "[]") ?: "[]"
            val history = try { JSONArray(historyRaw) } catch (_: Exception) { JSONArray() }
            val points = (0 until history.length()).mapNotNull { history.optJSONObject(it)?.optDouble("p")?.takeIf { n -> n.isFinite() } }
            val change = if (points.size > 1 && points.first() != 0.0) (points.last() - points.first()) / points.first() * 100.0 else null
            val activeAlarms = SflData.alarms(context)
            val alarmCount = (0 until activeAlarms.length()).count { i ->
                val a = activeAlarms.optJSONObject(i)
                a != null && a.optBoolean("enabled", true) && a.optString("crop").equals(name, true) && a.optString("market", "p2p") == market
            }
            val buyTargets = mutableListOf<Double>()
            val sellTargets = mutableListOf<Double>()
            for (i in 0 until activeAlarms.length()) {
                val a = activeAlarms.optJSONObject(i) ?: continue
                if (!a.optBoolean("enabled", true) || !a.optString("crop").equals(name, true) || a.optString("market", "p2p") != market) continue
                if (a.optString("kind") == "buy") buyTargets.add(a.optDouble("target")) else sellTargets.add(a.optDouble("target"))
            }
            val status = when (mode) {
                "change" -> change?.let { "Variación: ${if (it >= 0) "+" else ""}${"%.2f".format(it)}%" } ?: "Sin historial suficiente"
                "stats" -> if (points.isEmpty()) "Sin estadísticas" else "Mín ${fmt(points.minOrNull()!!)} · Máx ${fmt(points.maxOrNull()!!)} · Med ${fmt(points.average())}"
                "opportunity" -> when {
                    price == null -> "Esperando precio"
                    buyTargets.any { price <= it } -> "Objetivo de compra alcanzado"
                    sellTargets.any { price >= it } -> "Objetivo de venta alcanzado"
                    else -> "Sin objetivo alcanzado · $alarmCount alarmas"
                }
                "distance" -> {
                    val target = (buyTargets + sellTargets).minByOrNull { kotlin.math.abs(it - (price ?: it)) }
                    if (price == null || target == null) "Configura alarmas" else "Objetivo más cercano: ${fmt(target)} (${"%.1f".format(kotlin.math.abs(target-price)/max(kotlin.math.abs(target), 0.000001)*100)}%)"
                }
                else -> if (alarmCount == 0) "Sin alarmas configuradas" else "$alarmCount alarmas activas"
            }
            v.setTextViewText(statusId, status)
            val bitmap = when (mode) {
                "change" -> indicatorBitmap(change ?: 0.0, color, mode)
                "distance" -> indicatorBitmap(if (price != null && buyTargets.isNotEmpty()) 100.0 - (buyTargets.minOf { kotlin.math.abs(price-it) } / max(price, 0.000001) * 1000).coerceIn(0.0,100.0) else 0.0, color, mode)
                "stats" -> indicatorBitmap(if (points.isNotEmpty()) 100.0 else 0.0, color, mode)
                "opportunity" -> indicatorBitmap(if (price != null && (buyTargets.any { price <= it } || sellTargets.any { price >= it })) 100.0 else 20.0, color, mode)
                else -> chart(points, color)
            }
            v.setImageViewBitmap(chartId, bitmap)
        }

        private fun fmt(x: Double) = if (x >= 100) "%.1f".format(x) else if (x >= 1) "%.3f".format(x) else "%.4f".format(x)

        private fun chart(points: List<Double>, color: Int): Bitmap {
            val w = 300; val h = 100
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 4f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            c.drawLine(0f, h-2f, w.toFloat(), h-2f, Paint().apply { this.color = Color.rgb(51,68,93); strokeWidth = 1f })
            if (points.isNotEmpty()) {
                var lo = points.minOrNull()!!; var hi = points.maxOrNull()!!
                if (lo == hi) { lo *= 0.98; hi *= 1.02 }
                val span = max(hi-lo, 0.000001)
                val path = Path()
                points.forEachIndexed { i, value ->
                    val x = if (points.size == 1) w/2f else 4f + i.toFloat()/(points.size-1)*(w-8)
                    val y = h-6f - ((value-lo)/span).toFloat()*(h-16)
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                c.drawPath(path, line)
            }
            return b
        }

        private fun indicatorBitmap(value: Double, color: Int, mode: String): Bitmap {
            val w = 300; val h = 100
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(51,68,93); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
            c.drawLine(12f, 50f, 288f, 50f, base)
            val amount = (if (mode == "change") 50.0 + value.coerceIn(-50.0,50.0) else value).coerceIn(0.0,100.0)
            c.drawLine(12f, 50f, 12f + 276f * amount.toFloat()/100f, 50f, fill)
            return b
        }

        fun notifyPrice(context: Context, name: String, kind: String, price: Double, alarmId: String = name) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel("sfl_alerts", "Alertas SFL", NotificationManager.IMPORTANCE_HIGH))
            val notification = androidx.core.app.NotificationCompat.Builder(context, "sfl_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle((if (kind == "buy") "Comprar " else "Vender ") + name)
                .setContentText("Precio actual: ${fmt(price)}")
                .setAutoCancel(true).build()
            manager.notify(alarmId.hashCode(), notification)
        }
    }
}
