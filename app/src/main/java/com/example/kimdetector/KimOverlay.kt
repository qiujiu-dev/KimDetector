package com.example.kimdetector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/**
 * 屏幕悬浮窗：红圈 + 图片 + 右上角关闭按钮。
 */
object KimOverlay {

    private const val TAG = "KimDetector"
    private var windowManager: WindowManager? = null
    private var circleView: View? = null
    private var imageView: View? = null
    private var closeView: View? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, targetBounds: Rect?, imageAsset: String, onClose: () -> Unit) {
        dismiss(context)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        @Suppress("DEPRECATION")
        val screen = Point().also { wm.defaultDisplay.getRealSize(it) }

        // 1) 红圈：圈住检测到的文字区域
        if (targetBounds != null) {
            val maxSize = (screen.x * 0.6).toInt().coerceAtLeast(120)
            val size = maxOf(targetBounds.width(), targetBounds.height()).coerceIn(120, maxSize)
            val left = (targetBounds.centerX() - size / 2).coerceIn(0, (screen.x - size).coerceAtLeast(0))
            val top = (targetBounds.centerY() - size / 2).coerceIn(0, (screen.y - size).coerceAtLeast(0))

            val circle = CircleView(context)
            val lp = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = left
            lp.y = top
            try {
                wm.addView(circle, lp)
                circleView = circle
            } catch (t: Throwable) {
                Log.e(TAG, "circle overlay error", t)
            }
        }

        // 2) 图片悬浮窗（不可触摸，纯展示）
        val bitmap = try {
            context.assets.open(imageAsset).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return

        val scale = minOf(
            (screen.x * 0.88f) / bitmap.width,
            (screen.y * 0.78f) / bitmap.height,
            1.5f
        )
        val imageW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val imageH = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val image = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        val imageLp = WindowManager.LayoutParams(
            imageW,
            imageH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        imageLp.gravity = Gravity.CENTER
        try {
            wm.addView(image, imageLp)
            imageView = image
        } catch (t: Throwable) {
            Log.e(TAG, "image overlay error", t)
        }

        // 3) 右上角关闭按钮（独立可点击窗口，骑在图片右上角）
        val buttonSize = 64.dp(context)
        val close = TextView(context).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_close)
            setOnClickListener { onClose() }
        }
        val closeLp = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        closeLp.gravity = Gravity.TOP or Gravity.START
        closeLp.x = ((screen.x - imageW) / 2 + imageW - buttonSize / 2)
            .coerceIn(0, (screen.x - buttonSize).coerceAtLeast(0))
        closeLp.y = ((screen.y - imageH) / 2 - buttonSize / 2)
            .coerceIn(0, (screen.y - buttonSize).coerceAtLeast(0))
        try {
            wm.addView(close, closeLp)
            closeView = close
        } catch (t: Throwable) {
            Log.e(TAG, "close button overlay error", t)
        }
    }

    fun dismiss(context: Context) {
        val wm = windowManager ?: return
        fun remove(view: View?) {
            if (view != null) {
                try {
                    wm.removeView(view)
                } catch (_: Exception) {
                }
            }
        }
        remove(circleView)
        remove(imageView)
        remove(closeView)
        circleView = null
        imageView = null
        closeView = null
        windowManager = null
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density + 0.5f).toInt()

    private class CircleView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.RED
            strokeWidth = 10f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = paint.strokeWidth / 2f
            canvas.drawOval(pad, pad, width - pad, height - pad, paint)
        }
    }
}
