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
        root.addView(label("Mercado", 13f))
        source = Spinner(this)
        source.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Plaza (P2P)", "Sequence"))
        source.setSelection(if (prefs.getString("source", "p2p") == "seq") 1 else 0)
        root.addView(source)
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
                SflWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(save)
        root.addView(label("Añade el widget desde la pantalla de inicio: mantén pulsado un espacio vacío → Widgets → Alarma SFL.", 12f))
        root.addView(label("Android puede espaciar las actualizaciones en segundo plano. La frecuencia periódica mínima estándar es de 15 minutos.", 11f))
        setContentView(ScrollView(this).apply { addView(root) })
        SflWork.schedule(this)
    }
}
