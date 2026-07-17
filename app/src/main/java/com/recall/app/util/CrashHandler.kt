package com.recall.app.util

import android.app.Application
import android.content.Intent
import android.util.Log
import com.recall.app.presentation.ui.crash.CrashReportActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global uncaught-exception handler that:
 * 1. Writes the crash stack trace + recent logcat to a file in filesDir/crashes/
 * 2. Launches [CrashReportActivity] so the user can share the report
 *
 * Call [register] once in [Application.onCreate] before any other initialisation.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASHES_DIR = "crashes"

    fun register(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val reportFile = writeCrashReport(app, throwable)
                launchCrashActivity(app, reportFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash report", e)
            } finally {
                // Propagate to the default handler so the process terminates normally
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "CrashHandler registered")
    }

    private fun writeCrashReport(app: Application, throwable: Throwable): File {
        val crashesDir = File(app.filesDir, CRASHES_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val reportFile = File(crashesDir, "crash_$timestamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        reportFile.writeText(buildString {
            appendLine("=== Recall Crash Report ===")
            appendLine("Timestamp : $timestamp")
            appendLine("Thread    : ${Thread.currentThread().name}")
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(sw.toString())
        })

        Log.i(TAG, "Crash report written to ${reportFile.absolutePath}")
        return reportFile
    }

    private fun launchCrashActivity(app: Application, reportFile: File) {
        val intent = Intent(app, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_REPORT_PATH, reportFile.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        app.startActivity(intent)
    }
}
