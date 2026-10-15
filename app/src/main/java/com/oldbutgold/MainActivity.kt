package com.oldbutgold

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val inputs = linkedMapOf<String, EditText>()
    private lateinit var buffSwitch: Switch

    private fun addField(parent: LinearLayout, key: String, title: String, value: Int, hint: String = "") {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.rgb(75, 61, 35))
            setPadding(0, 10, 0, 0)
        })
        val edit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            this.hint = hint
            setText(if (value == 0) "" else value.toString())
        }
        inputs[key] = edit
        parent.addView(edit)
    }

    private fun read(key: String): Int = inputs[key]?.text?.toString()?.toIntOrNull() ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(94, 62, 0)
        window.navigationBarColor = Color.BLACK

        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(255, 248, 225))
        }
        scroll.addView(form)
        setContentView(scroll)

        val settings = AutomationSettings.load(this)
        form.addView(TextView(this).apply {
            text = "OldButGOLD"
            textSize = 28f
            setTextColor(Color.rgb(143, 94, 0))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 6)
        })
        form.addView(TextView(this).apply {
            text = "MU Immortal · Sarı boss otomasyonu"
            textSize = 16f
            setPadding(0, 0, 0, 14)
        })

        addField(form, "minAtk", "Minimum ATK", settings.minAtk)
        addField(form, "minDef", "Minimum DEF", settings.minDef)
        addField(form, "radiusPx", "Boss tarama yarıçapı (piksel)", settings.radiusPx)
        addField(form, "buffX", "Buff için chat koordinatı X", settings.buffX)
        addField(form, "buffY", "Buff için chat koordinatı Y", settings.buffY)
        addField(form, "randomX", "Mini harita Random düğmesi X", settings.randomX)
        addField(form, "randomY", "Mini harita Random düğmesi Y", settings.randomY)
        addField(form, "mapLeft", "Mini harita sol sınırı X", settings.mapLeft)
        addField(form, "mapTop", "Mini harita üst sınırı Y", settings.mapTop)
        addField(form, "mapRight", "Mini harita sağ sınırı X", settings.mapRight)
        addField(form, "mapBottom", "Mini harita alt sınırı Y", settings.mapBottom)

        buffSwitch = Switch(this).apply {
            text = "Buff kontrolü aktif"
            isChecked = settings.buffEnabled
            textSize = 16f
            setPadding(0, 14, 0, 14)
        }
        form.addView(buffSwitch)

        form.addView(Button(this).apply {
            text = "Ayarları kaydet"
            setOnClickListener {
                AutomationSettings(
                    minAtk = read("minAtk"),
                    minDef = read("minDef"),
                    radiusPx = read("radiusPx").coerceAtLeast(1),
                    buffX = read("buffX"), buffY = read("buffY"),
                    randomX = read("randomX"), randomY = read("randomY"),
                    mapLeft = read("mapLeft"), mapTop = read("mapTop"),
                    mapRight = read("mapRight"), mapBottom = read("mapBottom"),
                    buffEnabled = buffSwitch.isChecked
                ).save(this@MainActivity)
                Toast.makeText(this@MainActivity, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
            }
        })

        form.addView(Button(this).apply {
            text = "Erişilebilirlik ayarlarını aç"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        form.addView(TextView(this).apply {
            text = "Not: Bu sürüm ayarları kaydeder. OCR, sarı/soluk kurukafa tanıma, oyuncu-hedef çakışması ve otomatik savaş döngüsü henüz tamamlanmadı."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 12)
        })
    }
}
