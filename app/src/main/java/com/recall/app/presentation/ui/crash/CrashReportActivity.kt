package com.recall.app.presentation.ui.crash

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.recall.app.MainActivity
import com.recall.app.presentation.ui.theme.RecallTheme
import java.io.File

/**
 * Shown immediately after a crash (started by [com.recall.app.util.CrashHandler]).
 *
 * Displays the crash report (stack trace + logcat) in a scrollable monospace view
 * and offers two actions:
 *  - **Share** — sends the report file via the system share sheet to any app
 *    (email, Slack, WhatsApp, Drive, etc.)
 *  - **Restart** — re-launches [MainActivity] so the user can try again
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reportPath = intent.getStringExtra(EXTRA_REPORT_PATH)
        val reportText = reportPath
            ?.let { runCatching { File(it).readText() }.getOrNull() }
            ?: "No crash report found."

        setContent {
            RecallTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        Text(
                            text = "💥 App Crashed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "The crash report below includes the error and recent logs. " +
                                    "Tap Share to send it for diagnosis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { shareReport(reportPath) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("📤 Share Report")
                            }
                            OutlinedButton(
                                onClick = { restartApp() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Restart App")
                            }
                        }

                        // Scrollable crash log
                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF1A1A1A),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .verticalScroll(vScroll)
                                .horizontalScroll(hScroll)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = reportText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE8E8E8),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shareReport(reportPath: String?) {
        if (reportPath == null) {
            Toast.makeText(this, "No report file to share", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(reportPath)
        if (!file.exists()) {
            Toast.makeText(this, "Report file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Recall Crash Report — ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Recall app crash report attached. Please see the file for full stack trace and logcat.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share crash report via…"))
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_REPORT_PATH = "extra_report_path"
    }
}
