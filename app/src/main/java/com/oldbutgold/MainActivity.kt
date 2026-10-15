package com.oldbutgold

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private fun field(label: String, hint: String): EditText {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        wrapper.addView(TextView(this).apply { text = label; textSize = 14f })
        val edit = EditText(this).apply {
            this.hint = hint
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        wrapper.addView(edit)
        form.addView(wrapper)
        return edit
    }

    private lateinit var form: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(94, 62, 0)
        window.navigationBarColor = Color.BLACK

        val scroll = ScrollView(this)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(255, 248, 225))
        }
        scroll.addView(form)
        setContentView(scroll)

        form.addView(TextView(this).apply {
            text = "OldButGOLD"
            textSize = 28f
            setTextColor(Color.rgb(143, 94, 0))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        })
        form.addView(TextView(this).apply {
            text = "MU Immortal · Sarı boss otomasyonu"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        })

        field("Minimum ATK", "ATK eşiği")
        field("Minimum DEF", "DEF eşiği")
        field("Boss tarama yarıçapı (px)", "Örn. 120")
        field("Buff chat hedef X", "Ekran X koordinatı")
        field("Buff chat hedef Y", "Ekran Y koordinatı")

        form.addView(Switch(this).apply {
            text = "Buff kontrolü aktif"
            isChecked = true
            textSize = 16f
            setPadding(0, 12, 0, 12)
        })

        form.addView(TextView(this).apply {
            text = "Kurulum iskeleti. Oyun içi boss algılama, OCR, hedefe ulaşma ve buff akışı henüz uygulanmadı."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 12, 0, 12)
        })
        form.addView(Button(this).apply {
            text = "Erişilebilirlik ayarlarını aç"
            setOnClickListener {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
    }
}
