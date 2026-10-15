package com.oldbutgold

import android.content.Context

data class AutomationSettings(
    val minAtk: Int = 0,
    val minDef: Int = 0,
    val radiusPx: Int = 120,
    val buffX: Int = 0,
    val buffY: Int = 0,
    val randomX: Int = 0,
    val randomY: Int = 0,
    val mapLeft: Int = 0,
    val mapTop: Int = 0,
    val mapRight: Int = 0,
    val mapBottom: Int = 0,
    val buffEnabled: Boolean = true
) {
    companion object {
        private const val PREFS = "automation_settings"
        fun load(context: Context): AutomationSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AutomationSettings(
                minAtk = p.getInt("minAtk", 0),
                minDef = p.getInt("minDef", 0),
                radiusPx = p.getInt("radiusPx", 120),
                buffX = p.getInt("buffX", 0),
                buffY = p.getInt("buffY", 0),
                randomX = p.getInt("randomX", 0),
                randomY = p.getInt("randomY", 0),
                mapLeft = p.getInt("mapLeft", 0),
                mapTop = p.getInt("mapTop", 0),
                mapRight = p.getInt("mapRight", 0),
                mapBottom = p.getInt("mapBottom", 0),
                buffEnabled = p.getBoolean("buffEnabled", true)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("automation_settings", Context.MODE_PRIVATE).edit()
            .putInt("minAtk", minAtk).putInt("minDef", minDef)
            .putInt("radiusPx", radiusPx).putInt("buffX", buffX).putInt("buffY", buffY)
            .putInt("randomX", randomX).putInt("randomY", randomY)
            .putInt("mapLeft", mapLeft).putInt("mapTop", mapTop)
            .putInt("mapRight", mapRight).putInt("mapBottom", mapBottom)
            .putBoolean("buffEnabled", buffEnabled).apply()
    }
}
