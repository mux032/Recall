package com.recall.app.presentation.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    // For Android 13+, we need to handle the partial permission case
    val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    var hasFullPermission by remember {
        mutableStateOf(
            if (isAndroid13Plus) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasFullPermission = true
            onPermissionsGranted()
        } else {
            showRationale = true
        }
    }
    
    // Photo picker for Android 13+ when user selects only some photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia()
    ) { uris ->
        // User has granted access to selected photos
        // This triggers the scan which will now have access to at least some photos
        onPermissionsGranted()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Recall Storage Access",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Recall needs access to your device's images to find and index your screenshots securely on-device.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isAndroid13Plus) {
                Text(
                    text = "Note: On Android 13+, select 'Allow all' when prompted for full screenshot access. If you select specific photos, only those will be accessible.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (showRationale) {
                Text(
                    text = "Without storage access, Recall cannot function. Your data never leaves your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            Button(
                onClick = { 
                    if (isAndroid13Plus && showRationale) {
                        // If they previously denied, try photo picker as fallback
                        photoPickerLauncher.launch(PickVisualMediaRequest())
                    } else {
                        permissionLauncher.launch(
                            if (isAndroid13Plus) {
                                Manifest.permission.READ_MEDIA_IMAGES
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = if (showRationale) "Try Again" else "Grant Permission",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            if (showRationale && isAndroid13Plus) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest())
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Select Photos Instead", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
