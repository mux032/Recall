package com.recall.app.presentation.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.hilt.navigation.compose.hiltViewModel
import com.recall.app.domain.model.Screenshot
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
// ... rest of imports unchanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel = hiltViewModel()
) {
    val screenshot by viewModel.screenshot.collectAsState()
    var chatQuery by remember { mutableStateOf("") }
    var isEditingText by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                // This will be handled inside ExtractedTextSection if needed, 
                // but we can also handle it here for global clicks.
            })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Screenshot Section
            item {
                screenshot?.let {
                    HeroScreenshotSection(screenshot = it)
                }
            }

            // AI Summary Card
            item {
                AISummaryCard()
            }

            // Extracted Text Section
            item {
                screenshot?.let { scr ->
                    ExtractedTextSection(
                        ocrText = scr.ocrText,
                        isUserEdited = scr.isUserEdited,
                        isEditing = isEditingText,
                        onEditModeChange = { isEditingText = it },
                        onTextChanged = { editedText ->
                            viewModel.saveEditedOcrText(editedText)
                        },
                        onGenerateClick = {
                            viewModel.prioritizeOcr()
                        }
                    )
                }
            }

            // Suggested Actions
            item {
                SuggestedActions()
            }
        }
    }

    // Chat Bar floating at bottom
    ChatBarAtBottom(
        query = chatQuery,
        onQueryChange = { chatQuery = it },
        onSendClick = { }
    )
}

@Composable
private fun HeroScreenshotSection(
    screenshot: Screenshot
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Screenshot Image Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            ) {
                // Screenshot Image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(screenshot.filePath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Screenshot detail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.4f)
                                ),
                                startY = 300f,
                                endY = 1000f
                            )
                        )
                )
            }
        }

        // Screenshot Description Text
        Spacer(modifier = Modifier.height(12.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = screenshot.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Captured on ${formatTimestamp(screenshot.dateCreated)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AISummaryCard() {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Decorative blur effect
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-24).dp)
                .size(128.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(64.dp)
                )
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "On-Device AI Summary",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                        Text(
                            text = "Intelligence Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Text
                Text(
                    text = "This capture showcases a minimalist fintech dashboard emphasizing portfolio liquidity and risk distribution. The UI leverages technical teals and deep navy to convey security and market authority.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractedTextSection(
    ocrText: String?,
    isUserEdited: Boolean = false,
    isEditing: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit = {},
    onGenerateClick: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current

    var extractedText by remember {
        mutableStateOf(ocrText ?: "")
    }

    // Update text when ocrText changes (from ViewModel)
    // Only update if not currently editing to avoid losing user's in-progress edits
    LaunchedEffect(ocrText) {
        if (!isEditing) {
            extractedText = ocrText ?: ""
        }
    }

    val hasOcrText = !ocrText.isNullOrBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(isEditing) {
                if (isEditing) {
                    detectTapGestures(onTap = {
                        onTextChanged(extractedText)
                        onEditModeChange(false)
                    })
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = if (isUserEdited) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Extracted Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isUserEdited) {
                            Text(
                                text = "Edited by you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Edit/Generate and Copy Icons in Rectangle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit or Generate Icon
                        IconButton(
                            onClick = {
                                if (isEditing) {
                                    onTextChanged(extractedText)
                                    onEditModeChange(false)
                                } else if (hasOcrText) {
                                    onEditModeChange(true)
                                } else {
                                    onGenerateClick()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            val icon = when {
                                isEditing -> Icons.Default.Check
                                hasOcrText -> Icons.Default.Edit
                                else -> Icons.Default.AutoAwesome
                            }
                            val tint = if (!hasOcrText && !isEditing) {
                                Color(0xFF4CAF50) // Green for generation icon
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = if (isEditing) "Save" else if (hasOcrText) "Edit" else "Generate",
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Vertical Divider
                        Surface(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {}

                        // Copy Icon
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(extractedText))
                            },
                            enabled = hasOcrText,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (hasOcrText) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            )

            // Text Content
            if (isEditing) {
                TextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 8,
                    maxLines = 12
                )
            } else {
                // Selectable Text or Placeholder
                if (hasOcrText) {
                    SelectionContainer {
                        Text(
                            text = extractedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No text extracted yet. Click the generation icon to start OCR.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedActions() {
    Column {
        Text(
            text = "Recommended Actions",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionChip(
                icon = Icons.Default.CalendarToday,
                label = "Add to Calendar",
                onClick = { }
            )
            ActionChip(
                icon = Icons.Default.Payments,
                label = "Track Expense",
                onClick = { }
            )
            ActionChip(
                icon = Icons.Default.Search,
                label = "Similar Designs",
                onClick = { }
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChatBarAtBottom(
    query: String,
    onQueryChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Icon
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Input Field
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Ask anything about this screenshot...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true
                )

                // Send Button
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// Helper Functions
@Composable
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun getSampleScreenshot(): Screenshot {
    return Screenshot(
        id = "sample_1",
        filePath = "",
        fileName = "sample.png",
        dateCreated = System.currentTimeMillis(),
        dateIndexed = System.currentTimeMillis(),
        width = 1080,
        height = 2340,
        ocrText = """Total Balance: ${'$'}142,509.20
Monthly Portfolio Growth: +12.4% since September
Assets under management consist of 45% Equity, 30% Fixed Income, and 25% Cryptocurrencies.
Recommended Action: Rebalance high-risk assets to maintain risk profile.""",
        appName = "Finance.Flow",
        description = "Fintech dashboard with portfolio overview",
        timestamp = System.currentTimeMillis()
    )
}
