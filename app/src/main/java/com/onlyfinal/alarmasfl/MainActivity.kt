package com.onlyfinal.alarmasfl

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import org.json.JSONArray

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("sfl", Context.MODE_PRIVATE) }
    private lateinit var source: Spinner
    private lateinit var cropForAlarm: Spinner
    private lateinit var alarmKind: Spinner
    private lateinit var alarmTarget: EditText
    private lateinit var alarmList: LinearLayout
    private lateinit var slotOne: Spinner
    private lateinit var slotTwo: Spinner
    private lateinit var widgetMode: Spinner
    private lateinit var updateLabel: TextView
    private lateinit var errorLabel: TextView
    private lateinit var pricesBox: LinearLayout
    private var crops: List<String> = listOf("Pepper", "Blueberry")

    private val modes = listOf("Gráfica", "Variación porcentual", "Proximidad a objetivos", "Estadísticas", "Oportunidad")
    private val modeKeys = listOf("graph", "change", "distance", "stats", "opportunity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF16202E.toInt())
        }
        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFFEEF2F7.toInt()); setPadding(0, 12, 0, 6)
        }
        fun spinner(items: List<String>) = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        }
        root.addView(label("ALARMA SFL", 22f))
        updateLabel = label("Sin datos", 11f).apply { setTextColor(0xFF93A3BA.toInt()) }
        root.addView(updateLabel)
        errorLabel = label("", 11f).apply { setTextColor(0xFFFFB4A8.toInt()) }
        root.addView(errorLabel)

        root.addView(label("Mercado", 13f))
        source = spinner(listOf("Plaza (P2P)", "Sequence"))
        source.setSelection(if (prefs.getString("source", "p2p") == "seq") 1 else 0)
        source.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadCrops()
                refreshPrices()
                refreshAlarmCropSpinner()
            }
        }
        root.addView(source)

        root.addView(label("PRECIOS DISPONIBLES", 16f))
        pricesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(pricesBox)

        root.addView(label("CONFIGURAR WIDGET", 16f))
        root.addView(label("Cultivo del espacio 1", 13f))
        slotOne = spinner(crops); root.addView(slotOne)
        root.addView(label("Cultivo del espacio 2", 13f))
        slotTwo = spinner(crops); root.addView(slotTwo)
        root.addView(label("Visualización para ambos espacios", 13f))
        widgetMode = spinner(modes); root.addView(widgetMode)
        root.addView(android.widget.Button(this).apply {
            text = "Guardar widget"
            setOnClickListener {
                prefs.edit()
                    .putString("widget_crop_1", slotOne.selectedItem?.toString() ?: "Pepper")
                    .putString("widget_crop_2", slotTwo.selectedItem?.toString() ?: "Blueberry")
                    .putString("widget_mode", modeKeys.getOrElse(widgetMode.selectedItemPosition) { "graph" })
                    .apply()
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Widget actualizado", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(label("ALARMAS DE PRECIO", 16f))
        root.addView(label("Cultivo", 13f))
        cropForAlarm = spinner(crops); root.addView(cropForAlarm)
        root.addView(label("Condición", 13f))
        alarmKind = spinner(listOf("Comprar si baja", "Vender si sube")); root.addView(alarmKind)
        root.addView(label("Precio objetivo", 13f))
        alarmTarget = EditText(this).apply {
            hint = "Ej. 0.020"
            inputType = 8194
            setSingleLine(true); setTextColor(0xFFEEF2F7.toInt()); setHintTextColor(0xFF93A3BA.toInt())
        }
        root.addView(alarmTarget)
        root.addView(android.widget.Button(this).apply {
            text = "Añadir alarma"
            setOnClickListener {
                val target = alarmTarget.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val crop = cropForAlarm.selectedItem?.toString()
                if (target == null || target <= 0.0 || crop == null) {
                    Toast.makeText(this@MainActivity, "Introduce un precio válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                SflData.addAlarm(this@MainActivity, crop, currentMarket(), if (alarmKind.selectedItemPosition == 0) "buy" else "sell", target)
                alarmTarget.text.clear()
                refreshAlarmList()
                SflWidgetProvider.updateAll(this@MainActivity)
            }
        })
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(alarmList)
        root.addView(android.widget.Button(this).apply {
            text = "Actualizar precios ahora"
            setOnClickListener {
                saveMarket()
                SflWork.refreshNow(this@MainActivity)
                Toast.makeText(this@MainActivity, "Actualización solicitada", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(label("Las actualizaciones automáticas se realizan en segundo plano. Android puede espaciar las consultas; WorkManager utiliza un mínimo periódico de 15 minutos.", 11f))
        setContentView(ScrollView(this).apply { addView(root) })

        loadCrops()
        loadWidgetSettings()
        refreshPrices()
        refreshAlarmList()
        SflWork.schedule(this)
        SflWork.refreshNow(this)
    }

    private fun currentMarket() = if (source.selectedItemPosition == 1) "seq" else "p2p"

    private fun saveMarket() {
        prefs.edit().putString("source", currentMarket()).apply()
    }

    private fun loadCrops() {
        val raw = prefs.getString(SflData.cropsKey(currentMarket()), "[]") ?: "[]"
        val fromApi = try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }.distinct().sorted()
        } catch (_: Exception) { emptyList() }
        crops = (fromApi + listOf("Pepper", "Blueberry").filter { fallback ->
            fromApi.none { it.equals(fallback, true) }
        }).distinct()
        if (crops.isEmpty()) crops = listOf("Pepper", "Blueberry")
        val adapterItems = crops
        listOfNotNull(slotOneOrNull(), slotTwoOrNull(), cropForAlarmOrNull()).forEach {
            it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, adapterItems)
        }
        selectSpinner(slotOneOrNull(), prefs.getString("widget_crop_1", "Pepper") ?: "Pepper")
        selectSpinner(slotTwoOrNull(), prefs.getString("widget_crop_2", "Blueberry") ?: "Blueberry")
        refreshPrices()
    }

    private fun slotOneOrNull() = if (::slotOne.isInitialized) slotOne else null
    private fun slotTwoOrNull() = if (::slotTwo.isInitialized) slotTwo else null
    private fun cropForAlarmOrNull() = if (::cropForAlarm.isInitialized) cropForAlarm else null

    private fun selectSpinner(spinner: Spinner?, value: String) {
        if (spinner == null) return
        val index = crops.indexOfFirst { it.equals(value, true) }
        if (index >= 0) spinner.setSelection(index)
    }

    private fun refreshAlarmCropSpinner() {
        if (!::cropForAlarm.isInitialized) return
        cropForAlarm.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, crops)
    }

    private fun loadWidgetSettings() {
        selectSpinner(slotOne, prefs.getString("widget_crop_1", "Pepper") ?: "Pepper")
        selectSpinner(slotTwo, prefs.getString("widget_crop_2", "Blueberry") ?: "Blueberry")
        val mode = prefs.getString("widget_mode", "graph") ?: "graph"
        widgetMode.setSelection(modeKeys.indexOf(mode).coerceAtLeast(0))
    }

    private fun refreshPrices() {
        if (!::pricesBox.isInitialized) return
        pricesBox.removeAllViews()
        val market = currentMarket()
        crops.forEach { crop ->
            val price = prefs.getString(SflData.priceKey(market, crop), null)?.toDoubleOrNull()
                ?: prefs.getString("${crop}_price", null)?.toDoubleOrNull()
            val row = TextView(this).apply {
                text = "$crop: " + (price?.let { fmt(it) } ?: "--")
                textSize = 15f; setTextColor(0xFFEEF2F7.toInt()); setPadding(12, 8, 12, 8)
                setBackgroundColor(0xFF1F2C3F.toInt())
            }
            pricesBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 })
        }
    }

    private fun refreshAlarmList() {
        if (!::alarmList.isInitialized) return
        alarmList.removeAllViews()
        val all = SflData.alarms(this)
        if (all.length() == 0) {
            alarmList.addView(TextView(this).apply { text = "Todavía no hay alarmas configuradas."; setTextColor(0xFF93A3BA.toInt()) })
            return
        }
        for (i in 0 until all.length()) {
            val alarm = all.optJSONObject(i) ?: continue
            val id = alarm.optString("id")
            val crop = alarm.optString("crop")
            val kind = alarm.optString("kind")
            val target = alarm.optDouble("target")
            val enabled = alarm.optBoolean("enabled", true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 4, 4, 4); setBackgroundColor(0xFF1F2C3F.toInt())
            }
            val check = CheckBox(this).apply {
                text = "$crop · ${if (kind == "buy") "Comprar ≤" else "Vender ≥"} ${fmt(target)}"
                isChecked = enabled; setTextColor(0xFFEEF2F7.toInt())
                setOnCheckedChangeListener { _, checked ->
                    updateAlarm(id) { it.put("enabled", checked).put("armed", true) }
                    SflWidgetProvider.updateAll(this@MainActivity)
                }
            }
            val delete = Button(this).apply {
                text = "×"
                setOnClickListener {
                    val updated = SflData.alarms(this@MainActivity)
                    val kept = JSONArray()
                    for (j in 0 until updated.length()) {
                        val a = updated.optJSONObject(j) ?: continue
                        if (a.optString("id") != id) kept.put(a)
                    }
                    SflData.saveAlarms(this@MainActivity, kept)
                    refreshAlarmList()
                    SflWidgetProvider.updateAll(this@MainActivity)
                }
            }
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(delete)
            alarmList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 })
        }
    }

    private fun updateAlarm(id: String, change: (org.json.JSONObject) -> Unit) {
        val all = SflData.alarms(this)
        for (i in 0 until all.length()) {
            val alarm = all.optJSONObject(i) ?: continue
            if (alarm.optString("id") == id) change(alarm)
        }
        SflData.saveAlarms(this, all)
    }

    override fun onResume() {
        super.onResume()
        if (!::updateLabel.isInitialized) return
        val updated = prefs.getLong("updated", 0L)
        updateLabel.text = if (updated == 0L) "Sin datos" else "Actualizado: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(updated))
        errorLabel.text = prefs.getString("last_error", "") ?: ""
        loadCrops()
        refreshAlarmList()
    }

    private fun fmt(value: Double): String = when {
        value >= 100 -> "%.1f".format(value)
        value >= 1 -> "%.3f".format(value)
        else -> "%.4f".format(value)
    }
}
