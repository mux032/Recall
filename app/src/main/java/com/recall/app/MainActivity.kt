package com.recall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.foundation.isSystemInDarkTheme
import com.recall.app.data.local.UserPreferences
import com.recall.app.data.repository.PermissionRepository
import com.recall.app.data.worker.ScanExistingWorker
import com.recall.app.domain.model.ThemeMode
import com.recall.app.presentation.ui.permissions.PermissionScreen
import com.recall.app.presentation.ui.theme.RecallTheme
import com.recall.app.presentation.ui.navigation.RecallNavGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val themeMode by userPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            RecallTheme(useDarkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }
                    var isCheckingPermission by remember { mutableStateOf(true) }

                    // Check persisted permission state on app launch
                    LaunchedEffect(Unit) {
                        val granted = permissionRepository.isPermissionGranted.first()
                        permissionsGranted = granted
                        isCheckingPermission = false
                    }

                    if (isCheckingPermission) {
                        // Show loading while checking permission state
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading...")
                        }
                    } else if (!permissionsGranted) {
                        PermissionScreen(
                            onPermissionsGranted = {
                                scope.launch {
                                    permissionRepository.setPermissionGranted(true)
                                    permissionsGranted = true
                                    startInitialDeepScan()
                                }
                            }
                        )
                    } else {
                        // Permission already granted - trigger scan if needed
                        LaunchedEffect(Unit) {
                            startInitialDeepScan()
                        }
                        RecallNavGraph()
                    }
                }
            }
        }
    }

    private fun startInitialDeepScan() {
        val scanRequest = OneTimeWorkRequestBuilder<ScanExistingWorker>().build()
        val ocrRequest = OneTimeWorkRequestBuilder<com.recall.app.data.worker.BackgroundOcrWorker>().build()
        
        WorkManager.getInstance(this).beginWith(scanRequest).then(ocrRequest).enqueue()
    }
}
