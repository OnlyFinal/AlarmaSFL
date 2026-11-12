package com.onlyfinal.alarmasfl

import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.os.Build
import android.widget.RemoteViews
import kotlin.math.max

class SflWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        SflWork.schedule(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SflWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }
        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences("sfl", Context.MODE_PRIVATE)
            val v = RemoteViews(context.packageName, R.layout.widget_sfl)
            v.setTextViewText(R.id.market, if (p.getString("source","p2p") == "seq") "Sequence" else "Plaza (P2P)")
            val updated = p.getLong("updated", 0L)
            v.setTextViewText(R.id.updated, if (updated == 0L) "sin datos" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(updated)))
            setItem(context, v, p, "Pepper", R.id.pepper_price, R.id.pepper_chart, R.id.pepper_status, Color.rgb(255,90,74))
            setItem(context, v, p, "Blueberry", R.id.blueberry_price, R.id.blueberry_chart, R.id.blueberry_status, Color.rgb(108,140,255))
            v.setTextViewText(R.id.widget_error, p.getString("last_error", "") ?: "")
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, v)
        }
        private fun setItem(context: Context, v: RemoteViews, p: android.content.SharedPreferences, name: String,
                            priceId: Int, chartId: Int, statusId: Int, color: Int) {
            val price = p.getString("${name}_price", null)?.toDoubleOrNull()
            v.setTextViewText(priceId, price?.let { fmt(it) } ?: "--")
            val buy = p.getString("${name}_buy", "")?.toDoubleOrNull()
            val sell = p.getString("${name}_sell", "")?.toDoubleOrNull()
            val status = when {
                price != null && buy != null && price <= buy -> "COMPRAR ≤ ${fmt(buy)}"
                price != null && sell != null && price >= sell -> "VENDER ≥ ${fmt(sell)}"
                buy != null || sell != null -> "En espera"
                else -> "Configura límites"
            }
            v.setTextViewText(statusId, status)
            val history = p.getString("${name}_history", "[]") ?: "[]"
            v.setImageViewBitmap(chartId, chart(history, color))
        }
        private fun fmt(x: Double) = if (x >= 100) "%.1f".format(x) else if (x >= 1) "%.3f".format(x) else "%.4f".format(x)

        private fun chart(raw: String, color: Int): Bitmap {
            val w = 300; val h = 100
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.drawColor(Color.TRANSPARENT)
            val pts = mutableListOf<Double>()
            try {
                val a = org.json.JSONArray(raw)
                for (i in 0 until a.length()) pts.add(a.getJSONObject(i).optDouble("p"))
            } catch (_: Exception) {}
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 4f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(51,68,93); strokeWidth = 1f }
            c.drawLine(0f, h-2f, w.toFloat(), h-2f, grid)
            if (pts.isNotEmpty()) {
                val lo = pts.minOrNull()!!; val hi = pts.maxOrNull()!!
                val span = max(hi-lo, 0.000001)
                val path = Path()
                pts.forEachIndexed { i, value ->
                    val x = if (pts.size == 1) w/2f else 4f + i.toFloat()/(pts.size-1)*(w-8)
                    val y = h-6f - ((value-lo)/span).toFloat()*(h-16)
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                c.drawPath(path, line)
            }
            return b
        }

        fun notifyPrice(context: Context, name: String, kind: String, price: Double) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel("sfl_alerts", "Alertas SFL", NotificationManager.IMPORTANCE_HIGH))
            val title = (if (kind == "buy") "Comprar " else "Vender ") + name
            val notification = Notification.Builder(context, if (Build.VERSION.SDK_INT >= 26) "sfl_alerts" else "")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText("Precio actual: ${fmt(price)}")
                .setAutoCancel(true).build()
            manager.notify(name.hashCode(), notification)
        }
    }
}
