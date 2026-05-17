package com.recall.app.presentation.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.domain.model.*
import com.recall.app.util.MemoryClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val deviceProfile by viewModel.deviceProfile.collectAsState()
    val recommendedModel by viewModel.recommendedModel.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val cacheLimitOption by viewModel.cacheLimitOption.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    // Map DeviceProfile → SystemStatus for existing SystemStatusCards composable
    val systemStatus = remember(deviceProfile) {
        SystemStatus(
            isFloatingCaptureEnabled = true,
            npuUsage = 0,
            cpuUsage = deviceProfile.availableCores * 3, // approximate placeholder
            deviceHealth = DeviceHealth.OPTIMIZED
        )
    }

    // Map ModelDownloadState → AIModel for the embedding model card
    val embeddingModel = remember(recommendedModel, downloadState, downloadProgress) {
        // Use a shorter display name — strip the size suffix to keep it compact
        val shortName = recommendedModel.displayName
            .replace(Regex("""\s*\(.*\)"""), "").trim()
        AIModel(
            id = recommendedModel.fileName,
            name = shortName,
            description = "",
            size = "${recommendedModel.sizeBytes / 1_000_000} MB",
            iconType = ModelIconType.HUB,
            status = when (downloadState) {
                ModelDownloadState.READY -> ModelStatus.READY
                ModelDownloadState.DOWNLOADING -> ModelStatus.DOWNLOADING
                ModelDownloadState.FAILED -> ModelStatus.AVAILABLE
                ModelDownloadState.NONE -> ModelStatus.AVAILABLE
            },
            isRecommended = true,
            // Surface failure so ModelLibraryCard renders the Re-download (Refresh) icon
            warning = if (downloadState == ModelDownloadState.FAILED) "Download Failed — tap to retry" else null,
            downloadProgress = if (downloadState == ModelDownloadState.DOWNLOADING) {
                (downloadProgress * 100).toInt().coerceIn(0, 100)
            } else null
        )
    }

    // Action handler — context-aware based on current download state
    val onEmbeddingModelAction: (AIModel) -> Unit = {
        when (downloadState) {
            ModelDownloadState.READY -> viewModel.deleteModel()
            ModelDownloadState.DOWNLOADING -> viewModel.cancelModelDownload()
            ModelDownloadState.FAILED -> viewModel.startModelDownload() // re-download
            ModelDownloadState.NONE -> viewModel.startModelDownload()
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Appearance (theme mode) ──────────────────────────────────────
            item {
                SettingsSection(
                    title = "Appearance",
                    description = "Choose how the app looks"
                ) {
                    ThemeModeCard(
                        currentMode = themeMode,
                        onModeSelected = { viewModel.setThemeMode(it) }
                    )
                }
            }

            // ── AI Search (memory + embedding model) ────────────────────────
            item {
                SettingsSection(title = "AI Search", description = "Memory and embedding model for on-device search") {
                    RequiredMemoryCard(
                        currentOption = cacheLimitOption,
                        memoryClass = deviceProfile.memoryClass,
                        onOptionSelected = { viewModel.setCacheLimitOption(it) }
                    )
                    EmbeddingModelCard(
                        model = embeddingModel,
                        onActionClick = { onEmbeddingModelAction(embeddingModel) }
                    )
                }
            }


        }
    }
}

/**
 * Segmented button row that lets the user pick [ThemeMode.SYSTEM], [ThemeMode.LIGHT],
 * or [ThemeMode.DARK]. Selection is persisted immediately via [onModeSelected].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeCard(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val options = ThemeMode.entries
    val icons = listOf(
        Icons.Filled.BrightnessAuto,
        Icons.Filled.LightMode,
        Icons.Filled.DarkMode
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = currentMode == mode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        Icon(
                            imageVector = icons[index],
                            contentDescription = mode.displayName,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    }
                ) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Consistent section wrapper — outer rounded container with title/description header
 * above the card content. All inner cards share the same [MaterialTheme.colorScheme.surface]
 * background to look unified inside the container.
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section heading
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Card content — inner cards rendered on surface color
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun SettingsHeader() {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configure device intelligence and privacy settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Displays real device hardware info sourced from [SettingsViewModel.deviceProfile].
 * Satisfies acceptance criterion: "System info shows actual RAM and CPU cores".
 */
@Composable
private fun DeviceProfileCard(
    totalRamBytes: Long,
    availableCores: Int,
    primaryAbi: String,
    modifier: Modifier = Modifier
) {
    val ramGb = totalRamBytes / (1024L * 1024 * 1024)
    val ramLabel = if (ramGb > 0) "$ramGb GB RAM" else "${totalRamBytes / (1024 * 1024)} MB RAM"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(
                        text = ramLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = "$availableCores cores",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "CPU",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = primaryAbi,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ABI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceHealthCard(
    systemStatus: SystemStatus
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NPU gauge
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = systemStatus.npuUsage / 100f,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        text = "${systemStatus.npuUsage}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = "NPU",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "AI processor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // CPU gauge
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = systemStatus.cpuUsage / 100f,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Text(
                        text = "${systemStatus.cpuUsage}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Column {
                    Text(
                        text = "CPU",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Processor cores",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureStateCard(
    systemStatus: SystemStatus,
    onFloatingCaptureToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.BubbleChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Floating Capture Bubble",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Quick access overlay for instant analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            var isChecked by remember { mutableStateOf(systemStatus.isFloatingCaptureEnabled) }
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onFloatingCaptureToggle(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredMemoryCard(
    currentOption: CacheLimitOption,
    memoryClass: MemoryClass,
    onOptionSelected: (CacheLimitOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = CacheLimitOption.entries.toList()

    // Reformat "50K embeddings (~75MB RAM)" → "~75 MB · 50K embeddings"
    fun formatOption(option: CacheLimitOption): String {
        if (option.estimatedMemoryMb == 0) return option.description // AUTO / UNLIMITED
        return "~${option.estimatedMemoryMb} MB · ${option.limit / 1_000}K embeddings"
    }

    // An option is too demanding if its memory requirement exceeds what the device class supports.
    // AUTO is always allowed. UNLIMITED requires VERY_HIGH. Others check estimatedMemoryMb.
    fun isExceedsDevice(option: CacheLimitOption): Boolean {
        if (option == CacheLimitOption.AUTO) return false
        if (option == CacheLimitOption.UNLIMITED) return memoryClass != MemoryClass.VERY_HIGH
        return when (memoryClass) {
            MemoryClass.LOW      -> option.estimatedMemoryMb > 75   // only CONSERVATIVE fits
            MemoryClass.MEDIUM   -> option.estimatedMemoryMb > 150  // up to BALANCED
            MemoryClass.HIGH     -> option.estimatedMemoryMb > 750  // up to AGGRESSIVE
            MemoryClass.VERY_HIGH -> false                           // all allowed
        }
    }

    fun requiresRamLabel(option: CacheLimitOption): String = when {
        option == CacheLimitOption.UNLIMITED -> "Requires 16 GB RAM"
        option.estimatedMemoryMb > 750       -> "Requires 16 GB RAM"
        option.estimatedMemoryMb > 150       -> "Requires 8 GB RAM"
        option.estimatedMemoryMb > 75        -> "Requires 4 GB RAM"
        else                                 -> ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Text(
                text = "Required Memory",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Full-width dropdown bar — the whole row is the trigger
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (expanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentOption.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatOption(currentOption),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                         else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select memory option",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Override Material3 menu container shape to match the rounded bar above
                MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        val disabled = isExceedsDevice(option)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (option == currentOption) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            option == currentOption -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = if (disabled) requiresRamLabel(option) else formatOption(option),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (disabled) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                if (!disabled) {
                                    onOptionSelected(option)
                                    expanded = false
                                }
                            },
                            enabled = !disabled,
                            leadingIcon = {
                                when {
                                    disabled -> Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    option == currentOption -> Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    else -> Spacer(modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
                } // end MaterialTheme shape override
            }

            // Restart hint
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Restart app to apply",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveModelCard(
    model: AIModel,
    onActionClick: () -> Unit
) {
    val containerColor = when (model.iconType) {
        ModelIconType.HUB -> MaterialTheme.colorScheme.secondaryContainer
        ModelIconType.PSYCHOLOGY -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconContentColor = when (model.iconType) {
        ModelIconType.HUB -> MaterialTheme.colorScheme.onSecondaryContainer
        ModelIconType.PSYCHOLOGY -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForType(model.iconType),
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (model.iconType) {
                            ModelIconType.HUB -> "Embedding Model"
                            ModelIconType.PSYCHOLOGY -> "Large Language Model"
                            else -> model.name
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = containerColor
                    ) {
                        Text(
                            text = model.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = iconContentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.size,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )

                    TextButton(
                        onClick = onActionClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (model.status) {
                                ModelStatus.RUNNING -> "Settings"
                                ModelStatus.READY -> "Update"
                                else -> "View"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelLibraryCard(
    model: AIModel,
    onActionClick: () -> Unit
) {
    val isDownloading = model.status == ModelStatus.DOWNLOADING
    val isIncompatible = model.status == ModelStatus.INCOMPATIBLE

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDownloading) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: model info taking all remaining width
                Column(modifier = Modifier.weight(1f)) {
                    // Model name on its own line
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Chips on the next line
                    if (model.isRequired || model.isRecommended) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (model.isRequired) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (model.isRecommended) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Recommended",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (model.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        when (model.status) {
                            ModelStatus.READY -> Text(
                                text = "✓ Ready",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            ModelStatus.AVAILABLE -> if (model.warning != null) {
                                Text(
                                    text = model.warning,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // Right: fixed-width action icon — always top-aligned
                val iconTint = when {
                    isIncompatible -> MaterialTheme.colorScheme.outline
                    model.status == ModelStatus.READY -> MaterialTheme.colorScheme.error
                    model.warning != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                val iconImage = when {
                    isDownloading -> Icons.Default.Close
                    isIncompatible -> Icons.Default.Block
                    model.status == ModelStatus.READY -> Icons.Default.Delete
                    model.warning != null -> Icons.Default.Refresh
                    else -> Icons.Default.Download
                }
                val iconDesc = when {
                    isDownloading -> "Cancel download"
                    isIncompatible -> "Not available"
                    model.status == ModelStatus.READY -> "Delete model"
                    model.warning != null -> "Re-download"
                    else -> "Download"
                }
                IconButton(
                    onClick = onActionClick,
                    enabled = !isIncompatible,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = iconImage,
                        contentDescription = iconDesc,
                        tint = iconTint
                    )
                }
            }

            // Download Progress bar — only while downloading
            if (isDownloading && model.downloadProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${model.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = model.downloadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

/**
 * Card for the AI Search section — shows "Embedding Model" as the fixed title
 * (not the file name) with status, size, chips, and context-aware action icon.
 */
@Composable
private fun EmbeddingModelCard(
    model: AIModel,
    onActionClick: () -> Unit
) {
    val isDownloading = model.status == ModelStatus.DOWNLOADING
    val isIncompatible = model.status == ModelStatus.INCOMPATIBLE

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDownloading) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Fixed title
                    Text(
                        text = "Embedding Model",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Model size
                    Text(
                        text = model.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Status below size — only show static states; downloading is shown via progress bar
                    when (model.status) {
                        ModelStatus.READY -> Text(
                            text = "✓ Downloaded",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ModelStatus.AVAILABLE -> Text(
                            text = if (model.warning != null) "Download failed — tap to retry"
                                   else "Tap to download",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (model.warning != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {}
                    }
                }

                // Context-aware action icon
                val iconTint = when {
                    isIncompatible -> MaterialTheme.colorScheme.outline
                    model.status == ModelStatus.READY -> MaterialTheme.colorScheme.error
                    model.warning != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                val iconImage = when {
                    isDownloading -> Icons.Default.Close
                    isIncompatible -> Icons.Default.Block
                    model.status == ModelStatus.READY -> Icons.Default.Delete
                    model.warning != null -> Icons.Default.Refresh
                    else -> Icons.Default.Download
                }
                val iconDesc = when {
                    isDownloading -> "Cancel download"
                    isIncompatible -> "Not available"
                    model.status == ModelStatus.READY -> "Delete model"
                    model.warning != null -> "Re-download"
                    else -> "Download"
                }
                IconButton(
                    onClick = onActionClick,
                    enabled = !isIncompatible,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = iconImage,
                        contentDescription = iconDesc,
                        tint = iconTint
                    )
                }
            }

            // Progress bar while downloading
            if (isDownloading && model.downloadProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${model.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = model.downloadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String?
) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsageTipCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Pro Tip: Resource Allocation",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Running multiple models simultaneously will increase battery drain. Disable the Floating Capture Bubble if you experience latency during model switching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun getIconForType(type: ModelIconType): ImageVector {
    return when (type) {
        ModelIconType.HUB -> Icons.Default.Hub
        ModelIconType.PSYCHOLOGY -> Icons.Default.Psychology
        ModelIconType.MODEL_TRAINING -> Icons.Default.ModelTraining
        ModelIconType.TOKEN -> Icons.Default.Token
        ModelIconType.AUTO_AWESOME -> Icons.Default.AutoAwesome
    }
}

// Sample Data
@Composable
fun getSampleSystemStatus(): SystemStatus {
    return SystemStatus(
        isFloatingCaptureEnabled = true,
        npuUsage = 14,
        cpuUsage = 23,
        deviceHealth = DeviceHealth.OPTIMIZED
    )
}

@Composable
fun getSampleActiveModels(): List<AIModel> {
    return listOf(
        AIModel(
            id = "embedding_1",
            name = "MobileClip-ViT-B-16",
            description = "Embedding Model",
            size = "450MB",
            iconType = ModelIconType.HUB,
            status = ModelStatus.RUNNING
        ),
        AIModel(
            id = "llm_1",
            name = "Gemma-2b-IT",
            description = "Large Language Model",
            size = "1.6GB",
            iconType = ModelIconType.PSYCHOLOGY,
            status = ModelStatus.RUNNING
        )
    )
}

@Composable
fun getSampleLanguageModels(): List<AIModel> {
    return listOf(
        AIModel(
            id = "llm_download",
            name = "Llama-3-8B-4bit",
            description = "High-performance reasoning engine",
            size = "4.7GB",
            iconType = ModelIconType.MODEL_TRAINING,
            status = ModelStatus.DOWNLOADING,
            isRecommended = true,
            warning = "High RAM usage",
            downloadProgress = 42
        ),
        AIModel(
            id = "llm_ready",
            name = "Phi-3-Mini",
            description = "Lightweight & Fast inference",
            size = "2.2GB",
            iconType = ModelIconType.MODEL_TRAINING,
            status = ModelStatus.READY
        )
    )
}

@Composable
fun getSampleEmbeddingModels(): List<AIModel> {
    return listOf(
        AIModel(
            id = "embed_required",
            name = "BGE-Small-v1.5",
            description = "Baseline for search & memory",
            size = "133MB",
            iconType = ModelIconType.HUB,
            status = ModelStatus.AVAILABLE,
            isRequired = true
        ),
        AIModel(
            id = "embed_incompatible",
            name = "E5-Mistral-7B",
            description = "State-of-the-art retrieval",
            size = "14.2GB",
            iconType = ModelIconType.TOKEN,
            status = ModelStatus.INCOMPATIBLE,
            warning = "Incompatible: Storage"
        )
    )
}
