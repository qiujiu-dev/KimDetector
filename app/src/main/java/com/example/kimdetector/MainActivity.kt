package com.example.kimdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusAccessibility: TextView
    private lateinit var statusWriteSettings: TextView
    private lateinit var statusOverlay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusAccessibility = findViewById(R.id.status_accessibility)
        statusWriteSettings = findViewById(R.id.status_write_settings)
        statusOverlay = findViewById(R.id.status_overlay)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_write_settings).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btn_test).setOnClickListener {
            KimTrigger.run(this, null)
        }

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val acc = isAccessibilityServiceEnabled()
        val write = Settings.System.canWrite(this)

        statusAccessibility.text = if (acc) "✓ 无障碍：已开启" else "✗ 无障碍：未开启"
        statusAccessibility.setTextColor(if (acc) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        statusWriteSettings.text =
            if (write) "✓ 修改系统设置：已授权" else "✗ 修改系统设置：未授权"
        statusWriteSettings.setTextColor(if (write) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val overlay = Settings.canDrawOverlays(this)
        statusOverlay.text = if (overlay) "✓ 显示在其他应用上层：已授权" else "✗ 显示在其他应用上层：未授权"
        statusOverlay.setTextColor(if (overlay) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${KimAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION = 100
    }
}
