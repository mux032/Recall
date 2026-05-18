package com.recall.app.presentation.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recall.app.domain.model.IndexingStats

/** Amber — used for the OCR (text reading) progress bar. */
private val OcrBarColor = Color(0xFFF59E0B)

/** Purple — used for the AI embedding progress bar. */
private val EmbeddingBarColor = Color(0xFF7C3AED)

/**
 * Dismissible banner that appears below the HomeScreen top bar while screenshots
 * are still being indexed in the background (Issue #106).
 *
 * Layout:
 * ```
 * ┌──────────────────────────────────────── [✕] ─┐
 * │  Indexing screenshots                          │
 * │  ┌─ label + progress bars ─┐  ┌─ ▶ button ─┐ │
 * │  │ 📄 Reading text… 3/10   │  │    round   │ │
 * │  │ ████░░░░░░  amber       │  │    play    │ │
 * │  │ 🧠 Building AI… 0/10    │  │   button   │ │
 * │  │ ░░░░░░░░░░  purple      │  └────────────┘ │
 * │  └─────────────────────────┘                  │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * @param isVisible   Whether the banner is shown.
 * @param stats       Live [IndexingStats] — drives bar progress values.
 * @param onDismiss   Called when user taps ✕.
 * @param onTapBanner Called when user taps the banner body → opens Settings.
 * @param onToggle    Called when user taps the round button → pauses if indexing is active, resumes if idle.
 */
@Composable
fun ProcessingStatusBanner(
    isVisible: Boolean,
    stats: IndexingStats,
    isIndexingActive: Boolean,
    onDismiss: () -> Unit,
    onTapBanner: () -> Unit,
    onToggle: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTapBanner)
                .semantics { contentDescription = buildAccessibilityDescription(stats) },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ── Header: title + dismiss ✕ ─────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Indexing screenshots",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss indexing banner",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // ── Progress bars (left) + toggle button (right, centred vertically) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: stacked progress bars
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!stats.isOcrComplete) {
                            ProgressRow(
                                icon = Icons.Default.TextSnippet,
                                iconTint = OcrBarColor,
                                label = if (stats.total > 0)
                                    "Reading text… ${stats.ocrDoneCount}/${stats.total}"
                                else
                                    "Reading text from screenshots…",
                                progress = stats.ocrProgress,
                                barColor = OcrBarColor
                            )
                        }
                        if (!stats.isEmbeddingComplete) {
                            ProgressRow(
                                icon = Icons.Default.Psychology,
                                iconTint = EmbeddingBarColor,
                                label = if (stats.total > 0)
                                    "Building index… ${stats.embeddingDoneCount}/${stats.total}"
                                else
                                    "Building index…",
                                progress = stats.embeddingProgress,
                                barColor = EmbeddingBarColor
                            )
                        }
                    }

                    // Right: toggle button — vertically centred between the two bars
                    FilledTonalIconButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics {
                                contentDescription = if (isIndexingActive)
                                    "Pause indexing" else "Resume indexing"
                            },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isIndexingActive)
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    progress: Float,
    barColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
    }
}

private fun buildAccessibilityDescription(stats: IndexingStats): String {
    val parts = mutableListOf<String>()
    if (!stats.isOcrComplete) {
        parts.add("Reading text from ${stats.ocrPendingCount} screenshots")
    }
    if (!stats.isEmbeddingComplete) {
        parts.add("Building AI search index for ${stats.embeddingPendingCount} screenshots")
    }
    return if (parts.isEmpty()) "Indexing complete"
    else "Processing in background: ${parts.joinToString(", ")}"
}
