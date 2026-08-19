package com.example.kimdetector

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KimAccessibilityService : AccessibilityService() {

    private val keywords = listOf("金正恩", "kim jong-un", "kim jong un", "kimjongun")
    private var lastScan = 0L
    private var lastTrigger = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val periodicScan = object : Runnable {
        override fun run() {
            scan()
            mainHandler.postDelayed(this, PERIODIC_SCAN_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.post(periodicScan)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (containsKeyword(event.text.joinToString(" "))) trigger(null)
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
    }

    private fun scan() {
        val now = System.currentTimeMillis()
        if (now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now
        val root = rootInActiveWindow ?: return
        val bounds = findKeyword(root)
        if (bounds != null) trigger(bounds)
    }

    private fun findKeyword(node: AccessibilityNodeInfo): Rect? {
        val text = node.text?.toString() ?: ""
        val description = node.contentDescription?.toString() ?: ""
        if (containsKeyword(text) || containsKeyword(description)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            return bounds
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findKeyword(child)
            if (result != null) return result
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
        KimTrigger.run(this, bounds)
    }

    fun closeOverlayAndStop() {
        KimOverlay.dismiss(this)
        KimTrigger.stopBgm(this)
    }

    override fun onInterrupt() {
        KimOverlay.dismiss(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mainHandler.removeCallbacks(periodicScan)
        instance = null
        KimOverlay.dismiss(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(periodicScan)
        instance = null
        KimOverlay.dismiss(this)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: KimAccessibilityService? = null
            private set

        private const val SCAN_INTERVAL_MS = 300L
        private const val PERIODIC_SCAN_INTERVAL_MS = 1_000L
        private const val TRIGGER_COOLDOWN_MS = 3_000L
    }
}
