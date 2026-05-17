package com.recall.app.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.recall.app.presentation.ui.theme.Inter
import kotlinx.coroutines.delay

/** Placeholder hints that rotate in the search bar when it is empty and unfocused. */
val SEARCH_HINTS = listOf(
    "Search screenshots with AI...",
    "Try: 'receipts from last month'",
    "Try: 'blue shirt design'",
    "Try: 'flight booking confirmation'",
    "Try: 'meeting notes'",
    "Try: 'handwritten address'"
)

/** How long each placeholder hint is shown before rotating to the next. */
const val HINT_ROTATION_INTERVAL_MS = 3_000L

/**
 * Unified, reusable search bar used on both the Home screen and the Search screen.
 *
 * Features (Issue #85):
 * - Rotating placeholder hints (every [HINT_ROTATION_INTERVAL_MS]) when bar is empty + unfocused
 * - No auto-focus — only focused when the user explicitly taps
 * - [interactionSource] exposed so callers can observe focus for history dropdown
 *
 * @param query             Current text field value.
 * @param onQueryChange     Called on every keystroke.
 * @param onSearch          Called when the user submits (IME Search key or search button).
 * @param interactionSource Shared interaction source — callers observe focus via
 *                          [interactionSource.collectIsFocusedAsState()].
 * @param modifier          Optional modifier applied to the outer [Surface].
 */
@Composable
fun RecallSearchBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Rotate placeholder index while bar is empty and not focused
    var hintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isFocused, query.text) {
        if (!isFocused && query.text.isEmpty()) {
            while (true) {
                delay(HINT_ROTATION_INTERVAL_MS)
                hintIndex = (hintIndex + 1) % SEARCH_HINTS.size
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search input
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        // Animate placeholder rotation when bar is empty + unfocused
                        if (!isFocused && query.text.isEmpty()) {
                            AnimatedContent(
                                targetState = hintIndex,
                                transitionSpec = {
                                    (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                                },
                                label = "hint_rotation"
                            ) { idx ->
                                Text(
                                    text = SEARCH_HINTS[idx],
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "Search your screenshots...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    interactionSource = interactionSource,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Inter),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )

                // Search button
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
