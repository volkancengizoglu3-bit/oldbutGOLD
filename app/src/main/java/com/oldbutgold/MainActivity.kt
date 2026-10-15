package com.oldbutgold

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("oldbutgold_settings", MODE_PRIVATE) }
    private val fields = linkedMapOf<String, EditText>()
    private lateinit var buffEnabled: CheckBox
    private val bg = Color.rgb(255, 248, 225)
    private val panel = Color.rgb(255, 255, 255)
    private val accent = Color.rgb(190, 126, 0)
    private val muted = Color.rgb(105, 92, 67)
    private val ink = Color.rgb(44, 37, 24)

    private val points = listOf(
        "Chat açma düğmesi" to GameAutomationService.KEY_CHAT,
        "Chat kapatma düğmesi" to GameAutomationService.KEY_CHAT_CLOSE,
        "Buff hedef koordinatı" to GameAutomationService.KEY_BUFF
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(94, 62, 0)
        window.navigationBarColor = Color.BLACK

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(18, 16, 18, 24) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root); page.addView(scroll); setContentView(page)

        root.addView(TextView(this).apply {
            text = "OLDBUTGOLD"; textSize = 12f; letterSpacing = .18f
            setTextColor(accent); setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "MU Immortal · Boss Hunter"; textSize = 29f; setTextColor(ink)
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 5, 0, 3)
        })
        root.addView(bodyText("Yüzen kontrolü oyun ekranında sürükleyebilirsin. Dokunarak paneli aç; paneldeki Kapat düğmesi paneli kapatır. Buff konumları ve eşikler burada ayarlanır."))

        val floating = card()
        floating.addView(sectionTitle("YÜZEN KONTROL"))
        floating.addView(actionButton("Üstte gösterme iznini aç", accent) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        floating.addView(actionButton("Yüzen kontrolü göster / yeniden aç", Color.rgb(116, 78, 8)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Önce üstte gösterme iznini aç.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else if (!GameAutomationService.isConnected()) {
                Toast.makeText(this, "Önce OldButGOLD erişilebilirlik servisini etkinleştir.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                GameAutomationService.showFloatingControls()
                Toast.makeText(this, "Yüzen simge hazır.", Toast.LENGTH_SHORT).show()
            }
        })
        floating.addView(actionButton("Erişilebilirlik ayarlarını aç", Color.rgb(85, 78, 61)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(floating, marginBottom())

        val coords = card()
        coords.addView(sectionTitle("01 / BUFF İÇİN DOKUNUŞ NOKTALARI"))
        coords.addView(bodyText("Her satırda Konum seç'e bas, oyun ekranına dön ve ilgili düğmeye/noktaya dokun. Konum seçim katmanı dokunuşu oyuna iletmez."))
        points.forEach { (label, key) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = label + if (pointSaved(key)) "  ✓" else "  •"
                tag = "label_$key"; textSize = 14f; setTextColor(ink)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(actionButton("Konum seç", accent) {
                if (!Settings.canDrawOverlays(this) || !GameAutomationService.isConnected()) {
                    Toast.makeText(this, "Önce üstte gösterme ve erişilebilirlik izinlerini etkinleştir.", Toast.LENGTH_LONG).show()
                    if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else GameAutomationService.beginPointSelection(key, label)
            })
            coords.addView(row)
        }
        root.addView(coords, marginBottom())

        val buff = card()
        buff.addView(sectionTitle("02 / BUFF EŞİKLERİ"))
        buff.addView(bodyText("Senin seçimine göre buff yalnızca ATK VE DEF değerlerinin ikisi de kendi alt sınırlarının altındaysa başlatılır. Buff noktasında OCR ile iki değer de eşik üstüne gelene kadar beklenir."))
        buffEnabled = CheckBox(this).apply {
            text = "Buff modunu etkinleştir"
            setTextColor(ink)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = prefs.getBoolean(GameAutomationService.KEY_BUFF_ENABLED, true)
        }
        buff.addView(buffEnabled)
        addSingle(buff, "ATK alt sınırı", GameAutomationService.KEY_ATK_THRESHOLD, 0)
        addSingle(buff, "DEF alt sınırı", GameAutomationService.KEY_DEF_THRESHOLD, 0)
        root.addView(buff, marginBottom())

        val save = card()
        save.addView(actionButton("Ayarları kaydet", accent) { saveSettings() })
        save.addView(bodyText("Not: Buff sistemi ve yüzen panel bu aşamada entegre ediliyor. Boss kurukafası algılama ve hedefe gitme akışı ayrı geliştirme adımlarıdır."))
        root.addView(save)
    }

    private fun pointSaved(key: String) = prefs.getInt("${key}_X", -1) in 0..1000 && prefs.getInt("${key}_Y", -1) in 0..1000

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && GameAutomationService.isConnected()) GameAutomationService.showFloatingControls()
    }

    private fun saveSettings() {
        val editor = prefs.edit()
        for ((key, field) in fields) {
            val value = field.text.toString().trim().toIntOrNull()
            if (value == null || value < 0) {
                Toast.makeText(this, "Geçerli, sıfır veya daha büyük bir sayı gir.", Toast.LENGTH_LONG).show()
                return
            }
            editor.putInt(key, value)
        }
        editor.putBoolean(GameAutomationService.KEY_BUFF_ENABLED, buffEnabled.isChecked)
        editor.apply()
        Toast.makeText(this, "Ayarlar kaydedildi.", Toast.LENGTH_SHORT).show()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
        background = rounded(panel, 18)
    }
    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value; textSize = 13f; letterSpacing = .08f; setTextColor(accent)
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 8)
    }
    private fun bodyText(value: String) = TextView(this).apply {
        text = value; textSize = 13f; setTextColor(muted); setPadding(0, 0, 0, 12)
    }
    private fun actionButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        background = rounded(color, 12); setPadding(10, 5, 10, 5); setOnClickListener { action() }
    }
    private fun addSingle(root: LinearLayout, label: String, key: String, default: Int) {
        root.addView(TextView(this).apply { text = label; textSize = 13f; setTextColor(muted); setPadding(0, 8, 0, 2) })
        val field = EditText(this).apply {
            hint = label; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine(true)
            textSize = 15f; setTextColor(ink); setHintTextColor(Color.rgb(150, 137, 111))
            background = rounded(Color.rgb(255, 250, 235), 10); setPadding(12, 8, 12, 8)
            setText(prefs.getInt(key, default).toString())
        }
        fields[key] = field; root.addView(field)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
    }
    private fun marginBottom() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = (14 * resources.displayMetrics.density).toInt()
    }
}
