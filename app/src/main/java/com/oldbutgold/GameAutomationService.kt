package com.oldbutgold

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/**
 * Reuses FarmBotInGen's coordinate-selection, chat-navigation, draggable floating
 * control, and threshold-based buff approach, adapted to OldButGOLD.
 *
 * Boss minimap recognition / route automation is not implemented in this file yet.
 */
class GameAutomationService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var popup: View? = null
    private var selectionView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var screenshotBusy = false
    private var buffMonitorRunning = false
    @Volatile private var bossHuntRunning = false
    private var bossPhase = BossPhase.NEED_MAP
    private var bossTargetX = 0f
    private var bossTargetY = 0f
    private var bossPhaseAt = 0L
    private var bossDeadFrames = 0
    private enum class BossPhase { NEED_MAP, WALKING, FIGHTING }
    private var state = BuffState.MONITORING
    private var lastBuffActionAt = 0L
    private var lastAtk: Int? = null
    private var lastDef: Int? = null
    private var selectionKey: String? = null
    private var selectionLabel: String? = null
    private val prefs by lazy { getSharedPreferences("oldbutgold_settings", MODE_PRIVATE) }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private enum class BuffState { MONITORING, GOING_TO_BUFF, WAITING_FOR_BUFF }

    private val detectorLoop = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !screenshotBusy) captureAndInspect()
            handler.postDelayed(this, 1800)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (android.provider.Settings.canDrawOverlays(this)) showFloatingControls()
        handler.post(detectorLoop)
        Log.i(TAG, "OldButGOLD accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun captureAndInspect() {
        if (screenshotBusy || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        screenshotBusy = true
        try {
            takeScreenshot(0, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var bitmap: Bitmap? = null
                    try {
                        bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        if (bitmap == null) { screenshotBusy = false; return }
                        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        bitmap.recycle()
                        if (bossHuntRunning) inspectBossFrame(mutable)
                        textRecognizer.process(InputImage.fromBitmap(mutable, 0))
                            .addOnSuccessListener { result -> inspectResult(result, mutable.width, mutable.height) }
                            .addOnFailureListener { e -> Log.w(TAG, "HUD OCR failed", e) }
                            .addOnCompleteListener { mutable.recycle(); screenshotBusy = false }
                    } catch (e: Exception) {
                        try { screenshot.hardwareBuffer.close() } catch (_: Exception) {}
                        bitmap?.recycle()
                        screenshotBusy = false
                        Log.e(TAG, "Screenshot processing failed", e)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    screenshotBusy = false
                    Log.w(TAG, "Screenshot failed: $errorCode")
                }
            })
        } catch (e: Exception) {
            screenshotBusy = false
            Log.e(TAG, "Screenshot request failed", e)
        }
    }

    private fun inspectResult(result: Text, width: Int, height: Int) {
        // Read only text detected in the upper-left HUD area.
        val topLeft = result.textBlocks.flatMap { block ->
            block.lines.filter { line ->
                val b = line.boundingBox
                b != null && b.left < width * 0.48 && b.top < height * 0.32
            }.map { it.text }
        }.joinToString(" ").lowercase(Locale.ROOT)

        lastAtk = extractStat(topLeft, listOf("atk", "attack"))
        lastDef = extractStat(topLeft, listOf("def", "defense", "defence"))
        bubble?.text = "G\nA:${lastAtk ?: "?"} D:${lastDef ?: "?"}"

        if (!buffMonitorRunning || !prefs.getBoolean(KEY_BUFF_ENABLED, true)) return
        val minAtk = prefs.getInt(KEY_ATK_THRESHOLD, 0)
        val minDef = prefs.getInt(KEY_DEF_THRESHOLD, 0)
        val atk = lastAtk
        val def = lastDef
        if (atk == null || def == null) return
        val now = System.currentTimeMillis()

        when (state) {
            BuffState.MONITORING -> {
                // User requirement: only trigger if BOTH values are below their minimums.
                if (atk < minAtk && def < minDef && now - lastBuffActionAt > 5000L) {
                    lastBuffActionAt = now
                    state = BuffState.GOING_TO_BUFF
                    travelToTarget(KEY_BUFF) {
                        state = BuffState.WAITING_FOR_BUFF
                        Toast.makeText(this, "Buff noktasına ulaşıldı; ATK ve DEF eşikleri bekleniyor.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            BuffState.WAITING_FOR_BUFF -> {
                if (atk >= minAtk && def >= minDef) {
                    state = BuffState.MONITORING
                    lastBuffActionAt = now
                    Toast.makeText(this, "ATK ve DEF eşikleri sağlandı.", Toast.LENGTH_SHORT).show()
                }
            }
            BuffState.GOING_TO_BUFF -> Unit
        }
    }

    private fun extractStat(text: String, labels: List<String>): Int? {
        for (label in labels) {
            val regex = Regex("(?:^|\\s)${Regex.escape(label)}\\s*[:：]?\\s*([0-9][0-9,]*)", RegexOption.IGNORE_CASE)
            val match = regex.find(text) ?: continue
            return match.groupValues[1].replace(",", "").toIntOrNull()
        }
        return null
    }

    fun startBuffMonitor() {
        if (!prefs.getBoolean(KEY_BUFF_ENABLED, true)) {
            Toast.makeText(this, "Buff modu ayarlardan kapalı.", Toast.LENGTH_SHORT).show()
            return
        }
        buffMonitorRunning = true
        state = BuffState.MONITORING
        Toast.makeText(this, "Buff ATK/DEF kontrolü başlatıldı.", Toast.LENGTH_SHORT).show()
        refreshPopup()
    }

    fun stopBuffMonitor() {
        buffMonitorRunning = false
        state = BuffState.MONITORING
        Toast.makeText(this, "Buff kontrolü durduruldu.", Toast.LENGTH_SHORT).show()
        refreshPopup()
    }

    fun showFloatingControls() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (bubble != null) return
        val density = resources.displayMetrics.density
        val icon = TextView(this).apply {
            text = "G"; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; background = circle(Color.rgb(198, 132, 0)); elevation = 10f
            contentDescription = "OldButGOLD yüzen kontrolünü aç"
        }
        val params = WindowManager.LayoutParams((54*density).toInt(), (54*density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - 72*density).toInt()
            y = (resources.displayMetrics.heightPixels * .35f).toInt()
        }
        icon.setOnTouchListener(object : View.OnTouchListener {
            var dx0=0f; var dy0=0f; var sx=0; var sy=0; var moved=false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { dx0=e.rawX; dy0=e.rawY; sx=params.x; sy=params.y; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-dx0).toInt(); val dy=(e.rawY-dy0).toInt()
                        if (kotlin.math.abs(dx)>6 || kotlin.math.abs(dy)>6) moved=true
                        params.x=sx+dx; params.y=sy+dy
                        try { windowManager?.updateViewLayout(icon, params) } catch (_:Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if(!moved) togglePopup(); return true }
                }
                return false
            }
        })
        try { windowManager?.addView(icon, params); bubble=icon } catch (_:Exception) { bubble=null }
    }

    private fun togglePopup() {
        if (popup != null) {
            try { windowManager?.removeView(popup) } catch (_:Exception) {}
            popup=null
            return
        }
        val density=resources.displayMetrics.density
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(16,14,16,14)
            background=rounded(Color.rgb(38,31,18),18,Color.rgb(214,157,34)); elevation=18f
        }
        container.addView(TextView(this).apply {
            text="OLDBUTGOLD  ⋮⋮"; textSize=14f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255,205,70)); setPadding(4,4,4,14)
        })
        addPopupAction(container, if (buffMonitorRunning) "■  Buff kontrolünü durdur" else "▶  Buff kontrolünü başlat", Color.rgb(166,108,0)) {
            if (buffMonitorRunning) stopBuffMonitor() else startBuffMonitor()
            try { windowManager?.removeView(container) } catch (_:Exception) {}
            popup=null
            togglePopup()
        }
        addPopupAction(container, if (bossHuntRunning) "■  Boss avını durdur" else "⚔  Boss avını başlat", Color.rgb(126,78,0)) { if (bossHuntRunning) stopBossHunt() else startBossHunt(); refreshPopup() }
        addPopupAction(container, "✦  Buff konumuna git", Color.rgb(89,72,39)) { travelToTarget(KEY_BUFF) }
        addPopupAction(container, "⚙  Ayarları aç", Color.rgb(89,72,39)) {
            startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addPopupAction(container, "—  Paneli kapat", Color.rgb(60,52,38)) {
            try { windowManager?.removeView(container) } catch (_:Exception) {}
            popup=null
        }
        addPopupAction(container, "×  Yüzen simgeyi gizle", Color.rgb(60,52,38)) {
            try { popup?.let { windowManager?.removeView(it) } } catch (_:Exception) {}
            popup=null
            try { bubble?.let { windowManager?.removeView(it) } } catch (_:Exception) {}
            bubble=null
        }
        val params=WindowManager.LayoutParams((280*density).toInt(),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START
            x=(resources.displayMetrics.widthPixels*.12f).toInt()
            y=(resources.displayMetrics.heightPixels*.25f).toInt()
        }
        try { windowManager?.addView(container,params); popup=container }
        catch (_:Exception) { Toast.makeText(this,"Yüzen panel açılamadı.",Toast.LENGTH_LONG).show() }
    }

    private fun refreshPopup() {
        if (popup != null) {
            try { windowManager?.removeView(popup) } catch (_:Exception) {}
            popup=null
            togglePopup()
        }
    }

    private fun addPopupAction(parent: LinearLayout,label:String,color:Int,action:()->Unit) {
        val density=resources.displayMetrics.density
        val item=TextView(this).apply {
            text=label; textSize=14f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
            setPadding(12,13,12,13); background=rounded(color,11); isClickable=true
            setOnClickListener { action() }
        }
        parent.addView(item,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(7*density).toInt() })
    }

    fun beginPointSelection(key:String,label:String) {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this,"Önce üstte gösterme iznini aç.",Toast.LENGTH_LONG).show(); return
        }
        selectionKey=key; selectionLabel=label
        val overlay=FrameLayout(this).apply { setBackgroundColor(Color.argb(35,0,0,0)) }
        val hint=TextView(this).apply {
            text="KONUM SEÇİMİ: $label\nİstenen noktaya bir kez dokun."
            textSize=16f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
            setPadding(20,18,20,18); background=rounded(Color.rgb(38,31,18),14,Color.rgb(255,194,51))
        }
        overlay.addView(hint,FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply { setMargins(12,36,12,0) })
        val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START }
        overlay.setOnTouchListener { _, event ->
            if (event.action==MotionEvent.ACTION_UP) {
                val x=(event.rawX/resources.displayMetrics.widthPixels*1000).toInt().coerceIn(0,1000)
                val y=(event.rawY/resources.displayMetrics.heightPixels*1000).toInt().coerceIn(0,1000)
                val keyToSave=selectionKey
                if (keyToSave!=null) prefs.edit().putInt("${keyToSave}_X",x).putInt("${keyToSave}_Y",y).apply()
                val savedLabel=selectionLabel
                removeSelection()
                Toast.makeText(this,"${savedLabel ?: "Hedef"} konumu kaydedildi.",Toast.LENGTH_SHORT).show()
                return@setOnTouchListener true
            }
            true
        }
        try { windowManager?.addView(overlay,p); selectionView=overlay }
        catch (_:Exception) { selectionView=null; Toast.makeText(this,"Konum seçimi açılamadı.",Toast.LENGTH_LONG).show() }
    }

    private fun removeSelection() {
        try { selectionView?.let { windowManager?.removeView(it) } } catch (_:Exception) {}
        selectionView=null; selectionKey=null; selectionLabel=null
    }

    private fun tapNormalized(x:Int,y:Int,done:(()->Unit)?=null) {
        val dm=resources.displayMetrics
        val px=(x.coerceIn(0,1000)/1000f*dm.widthPixels).coerceIn(0f,dm.widthPixels.toFloat()-1)
        val py=(y.coerceIn(0,1000)/1000f*dm.heightPixels).coerceIn(0f,dm.heightPixels.toFloat()-1)
        val path=Path().apply { moveTo(px,py) }
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build()
        dispatchGesture(gesture,object:GestureResultCallback(){
            override fun onCompleted(gestureDescription:GestureDescription?) { done?.invoke() }
            override fun onCancelled(gestureDescription:GestureDescription?) { done?.invoke() }
        },handler)
    }

    private fun travelToTarget(prefix:String,done:(()->Unit)?=null) {
        val chatX=prefs.getInt("${KEY_CHAT}_X",-1); val chatY=prefs.getInt("${KEY_CHAT}_Y",-1)
        val targetX=prefs.getInt("${prefix}_X",-1); val targetY=prefs.getInt("${prefix}_Y",-1)
        val closeX=prefs.getInt("${KEY_CHAT_CLOSE}_X",-1); val closeY=prefs.getInt("${KEY_CHAT_CLOSE}_Y",-1)
        if(!valid(chatX,chatY)||!valid(targetX,targetY)||!valid(closeX,closeY)) {
            Toast.makeText(this,"Chat açma/kapatma ve buff hedef konumlarını seç.",Toast.LENGTH_LONG).show()
            state=BuffState.MONITORING
            return
        }
        tapNormalized(chatX,chatY) { handler.postDelayed({
            tapNormalized(targetX,targetY) { handler.postDelayed({
                tapNormalized(closeX,closeY) { handler.postDelayed({ done?.invoke() },900) }
            },450) }
        },500) }
    }


    fun startBossHunt() {
        bossHuntRunning = true
        bossPhase = BossPhase.NEED_MAP
        bossPhaseAt = 0L
        bossDeadFrames = 0
        Toast.makeText(this, "Boss avı başlatıldı.", Toast.LENGTH_SHORT).show()
        refreshPopup()
    }

    fun stopBossHunt() {
        bossHuntRunning = false
        bossPhase = BossPhase.NEED_MAP
        Toast.makeText(this, "Boss avı durduruldu.", Toast.LENGTH_SHORT).show()
        refreshPopup()
    }

    private fun bossTap(x: Float, y: Float, done: (() -> Unit)? = null) {
        tapNormalized((x / resources.displayMetrics.widthPixels * 1000f).toInt(),
            (y / resources.displayMetrics.heightPixels * 1000f).toInt(), done)
    }

    private fun isBossGold(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r >= 150 && g >= 110 && b <= 120 && r - b >= 70 && r >= g && g > b
    }
    private fun isBossGreen(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r < 120 && g > 170 && b < 120 && g - r > 80
    }
    private fun isBossOrange(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r > 200 && g in 110..210 && b < 90
    }
    private fun colorAt(bitmap: Bitmap, nx: Float, ny: Float, orange: Boolean): Boolean {
        val cx = (nx * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val cy = (ny * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        var hits = 0; var total = 0
        for (y in (cy - bitmap.height * .018f).toInt().coerceAtLeast(0)..(cy + bitmap.height * .018f).toInt().coerceAtMost(bitmap.height - 1) step 3) {
            for (x in (cx - bitmap.width * .012f).toInt().coerceAtLeast(0)..(cx + bitmap.width * .012f).toInt().coerceAtMost(bitmap.width - 1) step 3) {
                total++
                if (if (orange) isBossOrange(bitmap.getPixel(x, y)) else isBossGold(bitmap.getPixel(x, y))) hits++
            }
        }
        return total > 0 && hits.toFloat() / total >= if (orange) .12f else .0012f
    }

    private fun inspectBossFrame(b: Bitmap) {
        if (!bossHuntRunning) return
        val now = System.currentTimeMillis()
        try {
            when (bossPhase) {
                BossPhase.NEED_MAP -> {
                    if (!colorAt(b, .894f, .157f, true)) {
                        if (now - bossPhaseAt > 1800L) {
                            bossPhaseAt = now
                            bossTap(.922f * b.width, .046f * b.height)
                        }
                        return
                    }
                    val map = findBossAndPlayer(b)
                    if (map.second == null) {
                        if (now - bossPhaseAt > 1600L) {
                            bossPhaseAt = now
                            bossTap(.868f * b.width, .814f * b.height)
                            Toast.makeText(this, "Yakında boss yok; Random kullanılıyor.", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    val player = map.second!!
                    val target = map.first.minByOrNull { kotlin.math.hypot((it.first-player.first).toDouble(), (it.second-player.second).toDouble()) }
                    if (target == null || kotlin.math.hypot((target.first-player.first).toDouble(), (target.second-player.second).toDouble()) > b.width * .06) {
                        if (now - bossPhaseAt > 1600L) {
                            bossPhaseAt = now
                            bossTap(.868f * b.width, .814f * b.height)
                        }
                        return
                    }
                    bossTargetX = target.first; bossTargetY = target.second
                    bossTap(bossTargetX, bossTargetY)
                    bossPhaseAt = now
                    bossPhase = BossPhase.WALKING
                }
                BossPhase.WALKING -> {
                    if (!colorAt(b, .894f, .157f, true)) {
                        if (now - bossPhaseAt > 1500L) { bossPhase = BossPhase.NEED_MAP; bossPhaseAt = 0L }
                        return
                    }
                    val map = findBossAndPlayer(b)
                    val player = map.second ?: return
                    val d = kotlin.math.hypot((bossTargetX-player.first).toDouble(), (bossTargetY-player.second).toDouble())
                    if (d < b.width * .018) {
                        bossTap(.894f * b.width, .157f * b.height) {
                            handler.postDelayed({ bossTap(.90f * resources.displayMetrics.widthPixels, .86f * resources.displayMetrics.heightPixels) }, 900)
                        }
                        bossPhase = BossPhase.FIGHTING
                        bossPhaseAt = now
                        bossDeadFrames = 0
                    } else if (now - bossPhaseAt > 60000L) {
                        bossPhase = BossPhase.NEED_MAP
                        bossPhaseAt = 0L
                    }
                }
                BossPhase.FIGHTING -> {
                    // Three consecutive frames without a bright-yellow skull indicate a kill.
                    val alive = colorAt(b, .925f, .075f, false)
                    if (!alive) bossDeadFrames++ else bossDeadFrames = 0
                    if (bossDeadFrames >= 3 || now - bossPhaseAt > 300000L) {
                        bossPhase = BossPhase.NEED_MAP
                        bossPhaseAt = 0L
                        bossDeadFrames = 0
                        Toast.makeText(this, if (alive) "Boss kontrol zaman aşımı; yeniden taranıyor." else "Boss öldü; sıradaki hedef aranıyor.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Boss frame inspection failed", e)
        }
    }

    private fun findBossAndPlayer(b: Bitmap): Pair<List<Pair<Float, Float>>, Pair<Float, Float>?> {
        val x0 = (b.width * .30f).toInt(); val x1 = (b.width * .84f).toInt()
        val y0 = (b.height * .18f).toInt(); val y1 = (b.height * .84f).toInt()
        val cell = maxOf(2, b.width / 850)
        val cw = (x1-x0)/cell; val ch = (y1-y0)/cell
        if (cw <= 0 || ch <= 0) return Pair(emptyList(), null)
        val counts = IntArray(cw*ch)
        var gx=0L; var gy=0L; var gn=0
        for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
            val col=b.getPixel(x,y)
            if (isBossGold(col)) counts[((y-y0)/cell).coerceIn(0,ch-1)*cw+((x-x0)/cell).coerceIn(0,cw-1)]++
            if (isBossGreen(col)) { gx+=x; gy+=y; gn++ }
        }
        val seen=BooleanArray(counts.size); val queue=IntArray(counts.size); val targets=ArrayList<Pair<Float,Float>>()
        for (i in counts.indices) {
            if (seen[i] || counts[i] < 5) continue
            var head=0; var tail=0; queue[tail++]=i; seen[i]=true
            var sx=0L; var sy=0L; var n=0
            while(head<tail) {
                val cur=queue[head++]; val cx=cur%cw; val cy=cur/cw
                sx+=cx; sy+=cy; n++
                for(dy in -1..1) for(dx in -1..1) {
                    val nx=cx+dx; val ny=cy+dy
                    if(nx<0||ny<0||nx>=cw||ny>=ch) continue
                    val ni=ny*cw+nx
                    if(!seen[ni] && counts[ni]>=5) { seen[ni]=true; queue[tail++]=ni }
                }
            }
            if(n in 8..250) targets.add(Pair(x0+(sx.toFloat()/n+.5f)*cell,y0+(sy.toFloat()/n+.5f)*cell))
        }
        val player=if(gn>8) Pair(gx.toFloat()/gn,gy.toFloat()/gn) else null
        return Pair(targets,player)
    }

    private fun valid(x:Int,y:Int)=x in 0..1000 && y in 0..1000
    private fun circle(color:Int)=GradientDrawable().apply {
        shape=GradientDrawable.OVAL
        setColor(color)
        setStroke((2*resources.displayMetrics.density).toInt(),Color.rgb(255,218,130))
    }
    private fun rounded(color:Int,radius:Int,stroke:Int?=null)=GradientDrawable().apply {
        setColor(color); cornerRadius=radius*resources.displayMetrics.density
        if(stroke!=null) setStroke((1*resources.displayMetrics.density).toInt(),stroke)
    }

    override fun onDestroy() {
        handler.removeCallbacks(detectorLoop)
        buffMonitorRunning=false
        bossHuntRunning=false
        removeSelection()
        try { popup?.let { windowManager?.removeView(it) } } catch (_:Exception) {}
        try { bubble?.let { windowManager?.removeView(it) } } catch (_:Exception) {}
        try { textRecognizer.close() } catch (_:Exception) {}
        if(instance===this) instance=null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OldButGOLD"
        const val KEY_CHAT="chat"
        const val KEY_CHAT_CLOSE="chat_close"
        const val KEY_BUFF="buff"
        const val KEY_BUFF_ENABLED="buff_enabled"
        const val KEY_ATK_THRESHOLD="atk_threshold"
        const val KEY_DEF_THRESHOLD="def_threshold"
        @Volatile private var instance:GameAutomationService?=null
        fun isConnected()=instance!=null
        fun showFloatingControls(){ instance?.showFloatingControls() }
        fun beginPointSelection(key:String,label:String){ instance?.beginPointSelection(key,label) }
    }
}
