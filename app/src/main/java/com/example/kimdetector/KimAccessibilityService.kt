package com.example.kimdetector

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class KimAccessibilityService : AccessibilityService() {

    private val keywords = listOf("金正恩", "kim jong-un", "kim jong un", "kimjongun")
    private var lastScan = 0L
    private var lastTrigger = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val periodicScan = object : Runnable {
        override fun run() {
            try {
                scan()
            } catch (t: Throwable) {
                Log.e(TAG, "periodic scan error", t)
            }
            mainHandler.postDelayed(this, PERIODIC_SCAN_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.removeCallbacks(periodicScan)
        mainHandler.post(periodicScan)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            if (event.packageName == packageName) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    val text = event.text?.joinToString(" ") ?: ""
                    if (containsKeyword(text)) trigger(null)
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> scan()
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val types = event.contentChangeTypes
                    if (types == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED ||
                        types and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT != 0
                    ) {
                        scan()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onAccessibilityEvent error", t)
        }
    }

    private fun scan() {
        val now = System.currentTimeMillis()
        if (now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now
        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            Log.e(TAG, "rootInActiveWindow error", t)
            null
        } ?: return
        val bounds = findKeyword(root)
        if (bounds != null) trigger(bounds)
    }

    /** 迭代遍历，避免深树递归爆栈；单节点异常不影响整体。 */
    private fun findKeyword(root: AccessibilityNodeInfo): Rect? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            try {
                val text = node.text?.toString() ?: ""
                val description = node.contentDescription?.toString() ?: ""
                if (containsKeyword(text) || containsKeyword(description)) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    while (queue.isNotEmpty()) {
                        try {
                            queue.poll()?.recycle()
                        } catch (_: Throwable) {
                        }
                    }
                    return bounds
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "node access error", t)
            } finally {
                try {
                    node.recycle()
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    private fun containsKeyword(text: String): Boolean {
        if (text.isEmpty()) return false
        val lower = text.lowercase()
        return keywords.any { lower.contains(it) }
    }

    private fun trigger(bounds: Rect?) {
        val now = System.currentTimeMillis()
        if (now - lastTrigger < TRIGGER_COOLDOWN_MS) return
        lastTrigger = now
        try {
            KimTrigger.run(this, bounds)
        } catch (t: Throwable) {
            Log.e(TAG, "trigger error", t)
        }
    }

    fun closeOverlayAndStop() {
        try {
            KimOverlay.dismiss(this)
            KimTrigger.stopBgm(this)
        } catch (t: Throwable) {
            Log.e(TAG, "close overlay error", t)
        }
    }

    override fun onInterrupt() {
        try {
            KimOverlay.dismiss(this)
        } catch (_: Throwable) {
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mainHandler.removeCallbacks(periodicScan)
        instance = null
        try {
            KimOverlay.dismiss(this)
        } catch (_: Throwable) {
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(periodicScan)
        instance = null
        try {
            KimOverlay.dismiss(this)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    companion object {
        const val TAG = "KimDetector"

        @Volatile
        var instance: KimAccessibilityService? = null
            private set

        private const val SCAN_INTERVAL_MS = 300L
        private const val PERIODIC_SCAN_INTERVAL_MS = 1_000L
        private const val TRIGGER_COOLDOWN_MS = 3_000L
    }
}
