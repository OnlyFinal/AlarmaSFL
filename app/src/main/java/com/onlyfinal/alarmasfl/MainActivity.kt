package com.onlyfinal.alarmasfl

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.work.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("sfl", Context.MODE_PRIVATE) }
    private lateinit var source: Spinner
    private lateinit var fields: MutableMap<String, EditText>
    private lateinit var priceLabels: MutableMap<String, TextView>
    private lateinit var changeLabels: MutableMap<String, TextView>
    private lateinit var updateLabel: TextView
    private lateinit var errorLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF16202E.toInt())
        }
        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFFEEF2F7.toInt())
            setPadding(0, 12, 0, 6)
        }
        root.addView(label("ALARMA SFL", 22f))
        updateLabel = label("Sin datos", 11f)
        updateLabel.setTextColor(0xFF93A3BA.toInt())
        root.addView(updateLabel)
        errorLabel = label("", 11f)
        errorLabel.setTextColor(0xFFFFB4A8.toInt())
        root.addView(errorLabel)
        root.addView(label("Mercado", 13f))
        source = Spinner(this)
        source.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Plaza (P2P)", "Sequence"))
        source.setSelection(if (prefs.getString("source", "p2p") == "seq") 1 else 0)
        root.addView(source)
        root.addView(label("PRECIOS ACTUALES", 16f))
        priceLabels = mutableMapOf()
        changeLabels = mutableMapOf()
        listOf("Pepper", "Blueberry").forEach { item ->
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 10, 14, 10)
                setBackgroundColor(0xFF1F2C3F.toInt())
            }
            val price = TextView(this).apply {
                text = "$item: --"; textSize = 22f; setTextColor(if (item == "Pepper") 0xFFFF5A4A.toInt() else 0xFF6C8CFF.toInt())
            }
            val change = TextView(this).apply { text = "Sin historial"; textSize = 12f; setTextColor(0xFF93A3BA.toInt()) }
            block.addView(price); block.addView(change)
            root.addView(block, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            priceLabels[item] = price; changeLabels[item] = change
        }
        root.addView(label("Límites de compra y venta", 16f))
        fields = mutableMapOf()
        listOf("Pepper", "Blueberry").forEach { item ->
            root.addView(label(item, 14f))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("buy", "sell").forEach { kind ->
                val edit = EditText(this).apply {
                    hint = if (kind == "buy") "Comprar si baja a" else "Vender si sube a"
                    setSingleLine(true); inputType = 8194
                    setTextColor(0xFFEEF2F7.toInt()); setHintTextColor(0xFF93A3BA.toInt())
                    setText(prefs.getString("${item}_$kind", ""))
                }
                row.addView(edit, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 })
                fields["${item}_$kind"] = edit
            }
            root.addView(row)
        }
        val save = Button(this).apply {
            text = "Guardar y actualizar"
            setOnClickListener {
                prefs.edit().apply {
                    putString("source", if (source.selectedItemPosition == 1) "seq" else "p2p")
                    fields.forEach { (k, v) -> putString(k, v.text.toString().trim()) }
                }.apply()
                SflWork.schedule(this@MainActivity)
                SflWork.refreshNow(this@MainActivity)
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(save)
        val refresh = Button(this).apply {
            text = "Actualizar precios ahora"
            setOnClickListener {
                SflWork.refreshNow(this@MainActivity)
                Toast.makeText(this@MainActivity, "Actualización solicitada", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(refresh)
        root.addView(label("Añade el widget desde la pantalla de inicio: mantén pulsado un espacio vacío → Widgets → Alarma SFL.", 12f))
        root.addView(label("Android puede espaciar las actualizaciones en segundo plano. La frecuencia periódica mínima estándar es de 15 minutos.", 11f))
        setContentView(ScrollView(this).apply { addView(root) })
        SflWork.schedule(this)
        SflWork.refreshNow(this)
    }

    override fun onResume() {
        super.onResume()
        if (!::priceLabels.isInitialized) return
        val updated = prefs.getLong("updated", 0L)
        updateLabel.text = if (updated == 0L) "Sin datos" else "Actualizado: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(updated))
        errorLabel.text = prefs.getString("last_error", "") ?: ""
        listOf("Pepper", "Blueberry").forEach { item ->
            val price = prefs.getString("${item}_price", null)?.toDoubleOrNull()
            priceLabels[item]?.text = "$item: " + (price?.let { fmt(it) } ?: "--")
            val history = try { org.json.JSONArray(prefs.getString("${item}_history", "[]")) } catch (_: Exception) { org.json.JSONArray() }
            val change = if (history.length() > 1) {
                val first = history.optJSONObject(0)?.optDouble("p", Double.NaN) ?: Double.NaN
                val last = history.optJSONObject(history.length() - 1)?.optDouble("p", Double.NaN) ?: Double.NaN
                if (first.isFinite() && first != 0.0 && last.isFinite()) ((last - first) / first * 100.0) else null
            } else null
            changeLabels[item]?.text = change?.let { (if (it >= 0) "+" else "") + "%.1f%% desde el primer registro".format(it) }
                ?: if (history.length() == 1) "1 punto registrado" else "Sin historial"
        }
    }

    private fun fmt(value: Double): String = when {
        value >= 100 -> "%.1f".format(value)
        value >= 1 -> "%.3f".format(value)
        else -> "%.4f".format(value)
    }
}
