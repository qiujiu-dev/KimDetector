package com.example.kimdetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        KimTrigger.setMaxBrightness(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification("亮度已拉满，正在播放《你若三冬来》…"))
        playMusic()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("准备播放…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("金正恩警报")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun playMusic() {
        try {
            val afd = assets.openFd(AUDIO_FILE)
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnCompletionListener { finishPrank() }
                setOnErrorListener { _, _, _ ->
                    finishPrank()
                    true
                }
                prepare()
                start()
            }
            afd.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "没找到 assets/$AUDIO_FILE，请把歌曲放进去喵",
                Toast.LENGTH_LONG
            ).show()
            finishPrank()
        }
    }

    private fun finishPrank() {
        KimOverlay.dismiss(this)
        KimTrigger.restoreBrightness(this)
        stopSelf()
    }

    fun stopPlayback() {
        finishPrank()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        KimTrigger.restoreBrightness(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        instance = null
        mediaPlayer?.release()
        mediaPlayer = null
        KimTrigger.restoreBrightness(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "金正恩警报",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        @Volatile
        var instance: MusicService? = null
            private set

        const val ACTION_STOP = "com.example.kimdetector.action.STOP"
        private const val CHANNEL_ID = "kim_alerts"
        private const val NOTIFICATION_ID = 1
        private const val AUDIO_FILE = "sandonglai.mp3"
    }
}
