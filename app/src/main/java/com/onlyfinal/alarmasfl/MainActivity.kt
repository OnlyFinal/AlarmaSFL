package com.onlyfinal.alarmasfl

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import org.json.JSONArray

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("sfl", Context.MODE_PRIVATE) }
    private lateinit var source: Spinner
    private lateinit var cropForAlarm: Spinner
    private lateinit var alarmBuy: EditText
    private lateinit var alarmSell: EditText
    private lateinit var alarmList: LinearLayout
    private lateinit var slotOne: Spinner
    private lateinit var slotTwo: Spinner
    private lateinit var widgetMode: Spinner
    private lateinit var priceCrop: Spinner
    private lateinit var priceValue: TextView
    private lateinit var updateLabel: TextView
    private lateinit var errorLabel: TextView
    private var crops: List<String> = listOf("Pepper", "Blueberry")
    private var updatingMarket = false

    private val modes = listOf("Gráfica", "Variación porcentual", "Proximidad a objetivos", "Estadísticas", "Oportunidad")
    private val modeKeys = listOf("graph", "change", "distance", "stats", "opportunity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        SflData.migrateLegacyAlarms(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF16202E.toInt())
        }
        fun label(value: String, size: Float = 14f, color: Int = 0xFFEEF2F7.toInt()) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 10, 0, 5)
            }
        fun heading(value: String) = label(value, 17f).apply {
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 7)
        }
        fun spinner(items: List<String>) = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        fun addField(title: String, field: EditText) {
            root.addView(label(title, 13f, 0xFFB8C7D9.toInt()))
            root.addView(field, LinearLayout.LayoutParams(-1, -2))
        }
        fun priceInput(hintText: String) = EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setTextColor(0xFFEEF2F7.toInt())
            setHintTextColor(0xFF93A3BA.toInt())
            setBackgroundColor(0xFF243349.toInt())
        }

        root.addView(label("ALARMA SFL", 23f).apply { setTypeface(null, android.graphics.Typeface.BOLD) })
        updateLabel = label("Sin datos", 11f, 0xFF93A3BA.toInt())
        root.addView(updateLabel)
        errorLabel = label("", 11f, 0xFFFFB4A8.toInt())
        root.addView(errorLabel)

        root.addView(heading("1. MERCADO"))
        source = spinner(listOf("Plaza (P2P)", "Sequence"))
        source.setSelection(if (prefs.getString("source", "p2p") == "seq") 1 else 0)
        root.addView(source)

        root.addView(heading("2. SELECCIONA LOS CULTIVOS DEL WIDGET"))
        root.addView(label("Espacio 1", 13f, 0xFFB8C7D9.toInt()))
        slotOne = spinner(crops); root.addView(slotOne)
        root.addView(label("Espacio 2", 13f, 0xFFB8C7D9.toInt()))
        slotTwo = spinner(crops); root.addView(slotTwo)
        root.addView(label("Qué información mostrar en los dos espacios", 13f, 0xFFB8C7D9.toInt()))
        widgetMode = spinner(modes); root.addView(widgetMode)
        root.addView(Button(this).apply {
            text = "GUARDAR CONFIGURACIÓN DEL WIDGET"
            setOnClickListener {
                prefs.edit()
                    .putString("widget_crop_1", slotOne.selectedItem?.toString() ?: "Pepper")
                    .putString("widget_crop_2", slotTwo.selectedItem?.toString() ?: "Blueberry")
                    .putString("widget_mode", modeKeys.getOrElse(widgetMode.selectedItemPosition) { "graph" })
                    .apply()
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(heading("3. PRECIOS DE LA ALARMA"))
        root.addView(label("Cultivo que quieres vigilar", 13f, 0xFFB8C7D9.toInt()))
        cropForAlarm = spinner(crops); root.addView(cropForAlarm)
        alarmBuy = priceInput("Comprar cuando baje a… (ej. 0.020)")
        addField("Precio de COMPRA (opcional)", alarmBuy)
        alarmSell = priceInput("Vender cuando suba a… (ej. 0.030)")
        addField("Precio de VENTA (opcional)", alarmSell)
        root.addView(label("Puedes indicar uno o los dos precios. Cada objetivo crea una alarma independiente.", 11f, 0xFF93A3BA.toInt()))
        root.addView(Button(this).apply {
            text = "GUARDAR ALARMA(S)"
            setOnClickListener {
                val crop = cropForAlarm.selectedItem?.toString()
                val buyText = alarmBuy.text.toString().trim().replace(',', '.')
                val sellText = alarmSell.text.toString().trim().replace(',', '.')
                val buy = buyText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val sell = sellText.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                if (crop == null || (buyText.isNotEmpty() && (buy == null || buy <= 0.0)) ||
                    (sellText.isNotEmpty() && (sell == null || sell <= 0.0)) ||
                    (buy == null && sell == null)) {
                    Toast.makeText(this@MainActivity, "Introduce un precio de compra, de venta o ambos.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (buy != null) SflData.addAlarm(this@MainActivity, crop, currentMarket(), "buy", buy)
                if (sell != null) SflData.addAlarm(this@MainActivity, crop, currentMarket(), "sell", sell)
                alarmBuy.text.clear()
                alarmSell.text.clear()
                refreshAlarmList()
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Alarma(s) guardada(s)", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(heading("4. ALARMAS CONFIGURADAS"))
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(alarmList)

        root.addView(heading("5. PRECIOS DISPONIBLES"))
        root.addView(label("Selecciona un cultivo para consultar su precio actual en el mercado elegido.", 11f, 0xFF93A3BA.toInt()))
        priceCrop = spinner(crops); root.addView(priceCrop)
        priceValue = label("Precio: --", 20f)
        root.addView(priceValue)
        priceCrop.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshSelectedPrice()
            }
        }

        root.addView(Button(this).apply {
            text = "ACTUALIZAR PRECIOS AHORA"
            setOnClickListener {
                saveMarket()
                SflWork.refreshNow(this@MainActivity)
                Toast.makeText(this@MainActivity, "Actualización solicitada", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(label("Las consultas automáticas se ejecutan en segundo plano. Android puede espaciar las actualizaciones.", 11f, 0xFF93A3BA.toInt()))

        source.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingMarket) return
                saveMarket()
                loadCrops()
                refreshAlarmList()
            }
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        loadCrops()
        loadWidgetSettings()
        refreshAlarmList()
        refreshSelectedPrice()
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
        crops = if (fromApi.isNotEmpty()) fromApi else listOf("Pepper", "Blueberry")
        val previousOne = prefs.getString("widget_crop_1", "Pepper") ?: "Pepper"
        val previousTwo = prefs.getString("widget_crop_2", "Blueberry") ?: "Blueberry"
        val previousAlarm = if (::cropForAlarm.isInitialized) cropForAlarm.selectedItem?.toString() else null
        val previousPrice = if (::priceCrop.isInitialized) priceCrop.selectedItem?.toString() else null
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, crops).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        if (::slotOne.isInitialized) slotOne.adapter = adapter
        if (::slotTwo.isInitialized) slotTwo.adapter = adapter
        if (::cropForAlarm.isInitialized) cropForAlarm.adapter = adapter
        if (::priceCrop.isInitialized) priceCrop.adapter = adapter
        selectSpinner(slotOneOrNull(), previousOne)
        selectSpinner(slotTwoOrNull(), previousTwo)
        selectSpinner(if (::cropForAlarm.isInitialized) cropForAlarm else null, previousAlarm ?: previousOne)
        selectSpinner(if (::priceCrop.isInitialized) priceCrop else null, previousPrice ?: previousOne)
        refreshSelectedPrice()
    }

    private fun slotOneOrNull() = if (::slotOne.isInitialized) slotOne else null
    private fun slotTwoOrNull() = if (::slotTwo.isInitialized) slotTwo else null

    private fun selectSpinner(spinner: Spinner?, value: String) {
        if (spinner == null) return
        val index = crops.indexOfFirst { it.equals(value, true) }
        spinner.setSelection(if (index >= 0) index else 0)
    }

    private fun loadWidgetSettings() {
        selectSpinner(slotOne, prefs.getString("widget_crop_1", "Pepper") ?: "Pepper")
        selectSpinner(slotTwo, prefs.getString("widget_crop_2", "Blueberry") ?: "Blueberry")
        widgetMode.setSelection(modeKeys.indexOf(prefs.getString("widget_mode", "graph")).coerceAtLeast(0))
    }

    private fun refreshSelectedPrice() {
        if (!::priceValue.isInitialized || !::priceCrop.isInitialized || priceCrop.selectedItem == null) return
        val crop = priceCrop.selectedItem.toString()
        val market = currentMarket()
        val price = prefs.getString(SflData.priceKey(market, crop), null)?.toDoubleOrNull()
            ?: prefs.getString("${crop}_price", null)?.toDoubleOrNull()
        priceValue.text = "Precio actual: " + (price?.let { fmt(it) } ?: "--")
    }

    private fun refreshAlarmList() {
        if (!::alarmList.isInitialized) return
        alarmList.removeAllViews()
        val all = SflData.alarms(this)
        var visible = 0
        for (i in 0 until all.length()) {
            val alarm = all.optJSONObject(i) ?: continue
            val id = alarm.optString("id")
            val crop = alarm.optString("crop")
            val kind = alarm.optString("kind")
            val target = alarm.optDouble("target")
            val market = alarm.optString("market", "p2p")
            if (market != currentMarket()) continue
            visible++
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 5, 4, 5)
                setBackgroundColor(0xFF1F2C3F.toInt())
            }
            val check = CheckBox(this).apply {
                text = "$crop · ${if (kind == "buy") "Compra ≤" else "Venta ≥"} ${fmt(target)}"
                isChecked = alarm.optBoolean("enabled", true)
                setTextColor(0xFFEEF2F7.toInt())
                setOnCheckedChangeListener { _, checked ->
                    updateAlarm(id) { it.put("enabled", checked).put("armed", true) }
                }
            }
            val delete = Button(this).apply {
                text = "Eliminar"
                setOnClickListener {
                    val latest = SflData.alarms(this@MainActivity)
                    val kept = JSONArray()
                    for (j in 0 until latest.length()) {
                        val a = latest.optJSONObject(j) ?: continue
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
        if (visible == 0) {
            alarmList.addView(TextView(this).apply {
                text = "No hay alarmas para este mercado."
                setTextColor(0xFF93A3BA.toInt())
            })
        }
    }

    private fun updateAlarm(id: String, change: (org.json.JSONObject) -> Unit) {
        val all = SflData.alarms(this)
        for (i in 0 until all.length()) {
            val alarm = all.optJSONObject(i) ?: continue
            if (alarm.optString("id") == id) change(alarm)
        }
        SflData.saveAlarms(this, all)
        SflWidgetProvider.updateAll(this)
    }

    override fun onResume() {
        super.onResume()
        if (!::updateLabel.isInitialized) return
        val updated = prefs.getLong("updated", 0L)
        updateLabel.text = if (updated == 0L) "Sin datos" else "Actualizado: " +
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(updated))
        errorLabel.text = prefs.getString("last_error", "") ?: ""
        loadCrops()
        refreshAlarmList()
        refreshSelectedPrice()
    }

    private fun fmt(value: Double): String = when {
        value >= 100 -> "%.1f".format(value)
        value >= 1 -> "%.3f".format(value)
        else -> "%.4f".format(value)
    }
}
