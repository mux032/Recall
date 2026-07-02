package com.recall.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.foundation.isSystemInDarkTheme
import com.recall.app.data.local.UserPreferences
import com.recall.app.data.repository.PermissionRepository
import com.recall.app.data.worker.IndexingPipelineWorker
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
                    // Always check the actual runtime permission — not just the DataStore flag.
                    // This correctly handles: permission revoked from Settings, fresh installs,
                    // and upgrades from older app versions that stored a stale granted flag.
                    val permissionsGranted by remember {
                        mutableStateOf(permissionRepository.hasActualPermission())
                    }

                    if (!permissionsGranted) {
                        PermissionScreen(
                            onPermissionsGranted = {
                                scope.launch {
                                    permissionRepository.setPermissionGranted(true)
                                    // Use REPLACE so this always runs after an explicit grant,
                                    // even if a stale KEEP worker is still in PENDING/RUNNING state.
                                    startInitialDeepScan(forceReplace = true)
                                }
                            }
                        )
                    } else {
                        // Permission already granted on launch — trigger scan via KEEP
                        // (RecallApplication already tried; this is a safety net for cases
                        // where the Application-level scan was skipped or failed).
                        LaunchedEffect(Unit) {
                            startInitialDeepScan(forceReplace = false)
                        }
                        RecallNavGraph()
                    }
                }
            }
        }
    }

    internal fun startInitialDeepScan(forceReplace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<IndexingPipelineWorker>()
            .addTag(RecallApplication.INDEXING_TAG)
            .build()
        val policy = if (forceReplace) {
            // After explicit permission grant: always run, even if a stale worker exists
            androidx.work.ExistingWorkPolicy.REPLACE
        } else {
            // Normal re-launch: don't interrupt an already-running scan
            androidx.work.ExistingWorkPolicy.KEEP
        }
        WorkManager.getInstance(this).enqueueUniqueWork(
            IndexingPipelineWorker.PIPELINE_WORK_NAME,
            policy,
            request
        )
        Log.i(TAG, "Enqueued IndexingPipelineWorker (forceReplace=$forceReplace)")
    }

    companion object {
        private const val TAG = "MainActivity"
        // Kept for backwards compatibility with existing tests
        internal const val INITIAL_SCAN_WORK_NAME = IndexingPipelineWorker.PIPELINE_WORK_NAME
    }
}
