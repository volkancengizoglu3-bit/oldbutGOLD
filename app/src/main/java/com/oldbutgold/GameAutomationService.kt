package com.oldbutgold

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Screenshot/OCR foundation. It deliberately does not tap the game yet.
 * Visual rules need to be calibrated against the actual game screenshots.
 */
class GameAutomationService : AccessibilityService() {
    private val tag = "OldButGOLD"
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val capturePending = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(tag, "Accessibility service connected; OCR diagnostic loop ready.")
        handler.post(captureLoop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler.removeCallbacks(captureLoop)
        Log.w(tag, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureLoop)
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    private val captureLoop = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && capturePending.compareAndSet(false, true)) {
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer,
                                    screenshot.colorSpace
                                )
                                if (bitmap != null) {
                                    runOcr(bitmap.copy(Bitmap.Config.ARGB_8888, false))
                                } else {
                                    Log.w(tag, "Screenshot could not be converted to bitmap.")
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Screenshot conversion failed.", e)
                            } finally {
                                screenshot.hardwareBuffer.close()
                                capturePending.set(false)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(tag, "Screenshot capture failed: code=$errorCode")
                            capturePending.set(false)
                        }
                    })
                } catch (e: Exception) {
                    capturePending.set(false)
                    Log.e(tag, "Screenshot request failed.", e)
                }
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block -> block.lines }.map { it.text }
                val atk = findStat(lines, "ATK")
                val def = findStat(lines, "DEF")
                if (atk != null || def != null) {
                    Log.i(tag, "HUD OCR: ATK=$atk DEF=$def; text=${lines.joinToString(" | ")}")
                } else {
                    Log.d(tag, "HUD OCR text: ${lines.joinToString(" | ")}")
                }
                bitmap.recycle()
            }
            .addOnFailureListener { e ->
                Log.e(tag, "OCR failed.", e)
                bitmap.recycle()
            }
    }

    private fun findStat(lines: List<String>, label: String): Int? {
        val labelRegex = Regex("(?i)\\b${label}\\b[^0-9]{0,12}([0-9][0-9, .]*)")
        for (line in lines) {
            val match = labelRegex.find(line)
            if (match != null) {
                val number = match.groupValues[1].replace(Regex("[^0-9]"), "")
                number.toIntOrNull()?.let { return it }
            }
        }
        return null
    }
}
