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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

object KimTrigger {

    private const val TAG = "KimDetector"
    private const val PREFS = "kim_prefs"
    private const val KEY_OLD_BRIGHTNESS = "old_brightness"
    private const val KEY_OLD_MODE = "old_mode"
    private const val KEY_HAS_SAVED = "has_saved"

    const val BRIGHTNESS_MAX = 255
    private const val IMAGE_ASSET = "kim_image.jpg"
    private const val NOTIFY_CHANNEL = "kim_commander"
    private const val NOTIFY_ID = 2

    fun run(context: Context, bounds: Rect?) {
        try {
            // 1. 亮度（可选权限，异常不影响后续）
            if (Settings.System.canWrite(context)) {
                try {
                    setMaxBrightness(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "brightness error", t)
                }
            } else {
                toast(context, "未授权「修改系统设置」，亮度保持原样喵")
            }

            // 2. 悬浮窗（红圈 + 图片 + 关闭按钮）
            val service = KimAccessibilityService.instance
            if (service != null && Settings.canDrawOverlays(context)) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        KimOverlay.show(context, bounds, IMAGE_ASSET) {
                            service.closeOverlayAndStop()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "overlay show error", t)
                    }
                }
            } else if (!Settings.canDrawOverlays(context)) {
                toast(context, "请授权「显示在其他应用上层」，否则图片无法展示喵")
            }

            // 3. 震动
            vibrate(context)

            // 4. BGM（前台服务被系统拒绝时降级为普通服务，不崩溃）
            startMusic(context)

            // 5. 通知
            try {
                postNotification(context)
            } catch (t: Throwable) {
                Log.e(TAG, "notification error", t)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "run error", t)
        }
    }

    fun stopBgm(context: Context) {
        try {
            MusicService.instance?.stopPlayback()
        } catch (t: Throwable) {
            Log.e(TAG, "stop bgm error", t)
        }
    }

    private fun startMusic(context: Context) {
        val intent = Intent(context, MusicService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "startForegroundService failed, fallback to startService", t)
            try {
                context.startService(intent)
            } catch (t2: Throwable) {
                Log.e(TAG, "startService failed", t2)
            }
        }
    }

    private fun toast(context: Context, message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
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
            val pattern = longArrayOf(0, 600, 150, 600, 150, 600, 150, 800)
            val effect = VibrationEffect.createWaveform(pattern, -1)

            if (Build.VERSION.SDK_INT >= 31) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = manager?.defaultVibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                    )
                    Log.i(TAG, "vibrate ok (VibratorManager)")
                    return
                }
            }

            @Suppress("DEPRECATION")
            val legacy = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (legacy != null && legacy.hasVibrator()) {
                try {
                    legacy.vibrate(effect)
                } catch (_: Throwable) {
                    legacy.vibrate(600)
                }
                Log.i(TAG, "vibrate ok (legacy Vibrator)")
            } else {
                Log.w(TAG, "no vibrator available")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "vibrate error", t)
            try {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(600)
            } catch (_: Throwable) {
            }
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
