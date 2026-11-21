package com.onlyfinal.alarmasfl

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.text.InputType
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
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
    private lateinit var historyPeriod: Spinner
    private lateinit var priceCrop: Spinner
    private lateinit var priceValue: TextView
    private lateinit var updateLabel: TextView
    private lateinit var errorLabel: TextView
    private var crops: List<String> = listOf("Pepper", "Blueberry")
    private var updatingMarket = false
    private val cropIconData: org.json.JSONObject by lazy {
        try { org.json.JSONObject(assets.open("crop_icons.json").bufferedReader().use { it.readText() }) }
        catch (_: Exception) { org.json.JSONObject() }
    }
    private val cropIconCache = mutableMapOf<String, Drawable?>()

    private fun cropIcon(name: String): Drawable? {
        val key = java.text.Normalizer.normalize(
            name.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]"), "")
        if (cropIconCache.containsKey(key)) return cropIconCache[key]
        val aliases = mapOf(
            "beet" to "beetroot",
            "sunflowerseed" to "sunflower",
            "blueberries" to "blueberry",
            "darkblueberries" to "darkblueberry",
            "redpepper" to "pepper"
        )
        val assetKey = aliases[key] ?: key
        val encoded = cropIconData.optString(assetKey).ifBlank { cropIconData.optString("default") }
        val drawable = try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            BitmapDrawable(resources, bitmap).apply {
                val size = (30 * resources.displayMetrics.density).toInt()
                setBounds(0, 0, size, size)
                paint.isFilterBitmap = false
            }
        } catch (_: Exception) { null }
        cropIconCache[key] = drawable
        return drawable
    }

    private val periodLabels = listOf("1 hora", "6 horas", "24 horas", "7 días")
    private val periodKeys = listOf("1h", "6h", "24h", "7d")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        SflData.migrateLegacyAlarms(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 24)
            setBackgroundColor(0xFFF1E8D2.toInt())
        }
        fun label(value: String, size: Float = 14f, color: Int = 0xFF334638.toInt()) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 10, 0, 5)
            }
        fun heading(value: String) = label(value, 15f, 0xFFF8F1DF.toInt()).apply {
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(13, 11, 13, 11)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF355B3E.toInt())
                cornerRadius = 12f
            }
        }
        fun spinner(items: List<String>) = Spinner(this).apply {
            adapter = styledSpinnerAdapter(items)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFCF4.toInt())
                cornerRadius = 10f
                setStroke(1, 0xFFB9A783.toInt())
            }
            setPadding(10, 4, 10, 4)
        }
        fun addField(title: String, field: EditText) {
            root.addView(label(title, 13f, 0xFF71806B.toInt()))
            root.addView(field, LinearLayout.LayoutParams(-1, -2))
        }
        fun priceInput(hintText: String) = EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setTextColor(0xFF263B2D.toInt())
            setHintTextColor(0xFF8B927E.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFCF4.toInt())
                cornerRadius = 10f
                setStroke(1, 0xFFD1C5A9.toInt())
            }
        }

        root.addView(label("🌻  ALARMASFL", 25f, 0xFF294B32.toInt()).apply {
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 8, 0, 0)
        })
        root.addView(label("HERRAMIENTAS DE MERCADO · SUNFLOWER LAND", 10f, 0xFF71806B.toInt()))
        updateLabel = label("Sin datos", 11f, 0xFF71806B.toInt())
        root.addView(updateLabel)
        errorLabel = label("", 11f, 0xFFFFB4A8.toInt())
        root.addView(errorLabel)

        root.addView(heading("1. MERCADO"))
        source = spinner(listOf("Plaza (P2P)", "Sequence"))
        source.setSelection(if (prefs.getString("source", "p2p") == "seq") 1 else 0)
        root.addView(source)

        root.addView(heading("2. ELIGE LOS CULTIVOS DEL WIDGET"))
        root.addView(label("Espacio 1", 13f, 0xFF71806B.toInt()))
        slotOne = spinner(crops); root.addView(slotOne)
        root.addView(label("Espacio 2", 13f, 0xFF71806B.toInt()))
        slotTwo = spinner(crops); root.addView(slotTwo)
        root.addView(label("Período de la gráfica y los indicadores", 13f, 0xFF71806B.toInt()))
        historyPeriod = spinner(periodLabels); root.addView(historyPeriod)
        root.addView(Button(this).apply {
            text = "GUARDAR CONFIGURACIÓN DEL WIDGET"
            setOnClickListener {
                prefs.edit()
                    .putString("widget_crop_1", slotOne.selectedItem?.toString() ?: "Pepper")
                    .putString("widget_crop_2", slotTwo.selectedItem?.toString() ?: "Blueberry")
                    .putString("history_period", periodKeys.getOrElse(historyPeriod.selectedItemPosition) { "24h" })
                    .apply()
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(heading("3. CONFIGURA LAS ALARMAS DE COMPRA Y VENTA"))
        root.addView(label("Cultivo que quieres vigilar", 13f, 0xFF71806B.toInt()))
        cropForAlarm = spinner(crops); root.addView(cropForAlarm)
        alarmBuy = priceInput("Comprar cuando baje a… (ej. 0.020)")
        addField("Precio de COMPRA (opcional)", alarmBuy)
        alarmSell = priceInput("Vender cuando suba a… (ej. 0.030)")
        addField("Precio de VENTA (opcional)", alarmSell)
        root.addView(label("Puedes indicar uno o los dos precios. Cada objetivo crea una alarma independiente.", 11f, 0xFF71806B.toInt()))
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

        root.addView(heading("5. CONSULTAR PRECIOS DISPONIBLES"))
        root.addView(label("Selecciona un cultivo para consultar su precio actual en el mercado elegido.", 11f, 0xFF71806B.toInt()))
        priceCrop = spinner(crops); root.addView(priceCrop)
        priceValue = label("Precio: --", 21f, 0xFF2F713F.toInt()).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
        root.addView(priceValue)

        root.addView(heading("4. ALARMAS CONFIGURADAS"))
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(alarmList)
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
        root.addView(label("Las consultas automáticas se ejecutan en segundo plano. Android puede espaciar las actualizaciones.", 11f, 0xFF71806B.toInt()))

        source.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (updatingMarket) return
                saveMarket()
                loadCrops()
                refreshAlarmList()
            }
        }

        fun applyControlStyle(view: View) {
            when (view) {
                is Button -> {
                    view.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF47764A.toInt())
                        cornerRadius = 12f
                        setStroke(1, 0xFF315B38.toInt())
                    }
                    view.setTextColor(0xFFFFF8E9.toInt())
                    view.textSize = 13f
                    view.isAllCaps = false
                    view.setPadding(14, 12, 14, 12)
                    view.elevation = 2f
                }
                is Spinner -> {
                    view.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFFFFFCF4.toInt())
                        cornerRadius = 10f
                        setStroke(1, 0xFFD1C5A9.toInt())
                    }
                    view.setPadding(10, 4, 10, 4)
                }
                is CheckBox -> view.buttonTintList = android.content.res.ColorStateList.valueOf(0xFF47764A.toInt())
            }
            if (view is LinearLayout) for (i in 0 until view.childCount) applyControlStyle(view.getChildAt(i))
        }
        applyControlStyle(root)
        window.statusBarColor = 0xFF294B32.toInt()
        window.navigationBarColor = 0xFF294B32.toInt()
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFFF1E8D2.toInt())
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

    private fun styledSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            private fun styledView(position: Int, parent: android.view.ViewGroup?, dropdown: Boolean): TextView {
                return (getItem(position) ?: "").let { value ->
                    TextView(this@MainActivity).apply {
                        text = value
                        textSize = 15f
                        setTextColor(0xFF263B2D.toInt())
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(14, if (dropdown) 13 else 9, 14, if (dropdown) 13 else 9)
                        if (dropdown) {
                            setBackgroundColor(0xFFFFFCF4.toInt())
                            minHeight = (48 * resources.displayMetrics.density).toInt()
                        }
                        val icon = cropIcon(value)
                        setCompoundDrawables(icon, null, null, null)
                        compoundDrawablePadding = (9 * resources.displayMetrics.density).toInt()
                    }
                }
            }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                styledView(position, parent, false)
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                styledView(position, parent, true)
        }
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
        val adapter = styledSpinnerAdapter(crops)
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
        historyPeriod.setSelection(periodKeys.indexOf(prefs.getString("history_period", "24h")).coerceAtLeast(0))
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
                setBackgroundColor(0xFFE5D8BA.toInt())
            }
            val check = CheckBox(this).apply {
                text = "$crop · ${if (kind == "buy") "Compra ≤" else "Venta ≥"} ${fmt(target)}"
                isChecked = alarm.optBoolean("enabled", true)
                setTextColor(0xFF334638.toInt())
                setCompoundDrawables(cropIcon(crop), null, null, null)
                compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
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
                setTextColor(0xFF71806B.toInt())
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
