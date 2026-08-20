package com.example.kimdetector

import android.app.Application
import android.util.Log
import java.io.File

/**
 * 记录未捕获崩溃堆栈到应用目录 crash.log，
 * 方便在真机上定位闪退原因。
 */
class App : Application() {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val file = File(dir, "crash.log")
                val text = buildString {
                    appendLine("=== crash at ${System.currentTimeMillis()} on ${thread.name} ===")
                    appendLine(Log.getStackTraceString(throwable))
                }
                file.appendText(text)
            } catch (_: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
