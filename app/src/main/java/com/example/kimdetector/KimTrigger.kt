package com.example.kimdetector

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat

object KimTrigger {

    private const val PREFS = "kim_prefs"
    private const val KEY_OLD_BRIGHTNESS = "old_brightness"
    private const val KEY_OLD_MODE = "old_mode"
    private const val KEY_HAS_SAVED = "has_saved"

    const val BRIGHTNESS_MAX = 255
    private const val IMAGE_ASSET = "kim_image.jpg"
    private const val NOTIFY_CHANNEL = "kim_commander"
    private const val NOTIFY_ID = 2

    fun run(context: Context, bounds: Rect?) {
        // 1. 亮度拉满（未授权时跳过，不影响其他功能）
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(
                context,
                "未授权「修改系统设置」，亮度保持原样喵",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            setMaxBrightness(context)
        }

        // 2. 红圈 + 图片悬浮窗
        val service = KimAccessibilityService.instance
        if (service != null && Settings.canDrawOverlays(context)) {
            Handler(Looper.getMainLooper()).post {
                KimOverlay.show(context, bounds, IMAGE_ASSET) {
                    service.closeOverlayAndStop()
                }
            }
        } else if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(
                context,
                "请授权「显示在其他应用上层」，否则图片无法展示喵",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. 震动
        vibrate(context)

        // 4. BGM
        context.startForegroundService(Intent(context, MusicService::class.java))

        // 5. 通知
        postNotification(context)
    }

    fun stopBgm(context: Context) {
        MusicService.instance?.stopPlayback()
    }

    fun setMaxBrightness(context: Context) {
        val resolver = context.contentResolver
        if (!Settings.System.canWrite(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_SAVED, false)) {
            val old = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val mode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            prefs.edit()
                .putInt(KEY_OLD_BRIGHTNESS, old)
                .putInt(KEY_OLD_MODE, mode)
                .putBoolean(KEY_HAS_SAVED, true)
                .apply()
        }

        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, BRIGHTNESS_MAX)
    }

    fun restoreBrightness(context: Context) {
        val resolver = context.contentResolver
        if (!Settings.System.canWrite(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_SAVED, false)) {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                prefs.getInt(KEY_OLD_BRIGHTNESS, 128)
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                prefs.getInt(KEY_OLD_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            )
            prefs.edit().putBoolean(KEY_HAS_SAVED, false).apply()
        }
    }

    private fun vibrate(context: Context) {
        try {
            val pattern = longArrayOf(0, 700, 200, 700, 200, 700)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= 31) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = manager.defaultVibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(effect)
                }
            }
        } catch (_: Exception) {
            // 部分 ROM 会限制后台震动，忽略异常
        }
    }

    private fun postNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFY_CHANNEL,
                "将军的恩情",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NOTIFY_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("将军的恩情还也换不够")
            .setContentText("将军的恩情还也换不够")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIFY_ID, notification)
    }
}
