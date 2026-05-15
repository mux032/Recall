package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * UI item representation for the home screen grid.
 * Sealed class ensures stable keys for LazyColumn items.
 */
sealed class UiItem {
    data class Header(val label: String) : UiItem()
    data class ScreenshotItem(val screenshot: Screenshot) : UiItem()
}

/**
 * A timeline section groups screenshots under a date header (e.g. "Today", "Yesterday").
 *
 * @param label    The section header text shown to the user.
 * @param screenshots Screenshots belonging to this section, ordered newest first.
 * @param subLabel Optional formatted date range shown below the header.
 */
data class TimelineSection(
    val label: String,
    val screenshots: List<Screenshot>,
    val subLabel: String = ""
)

/** Chronological ordering of timeline section labels, newest first. */
internal val TIMELINE_LABEL_ORDER =
    listOf("Today", "Yesterday", "This Week", "Last Week", "This Month", "Older")

/**
 * Transforms a flat list of [Screenshot]s into [TimelineSection]s.
 *
 * Steps:
 * 1. Deduplicate by ID
 * 2. Group by [getTimelineLabel]
 * 3. Sort groups in chronological order (newest first)
 * 4. Build [TimelineSection] with computed sub-labels
 */
internal fun buildTimelineSections(screenshots: List<Screenshot>): List<TimelineSection> {
    val unique = screenshots.distinctBy { it.id }

    val grouped = unique.groupBy { getTimelineLabel(it.timestamp) }

    val sorted = grouped.toSortedMap { a, b ->
        val indexA = TIMELINE_LABEL_ORDER.indexOf(a).takeIf { it >= 0 } ?: TIMELINE_LABEL_ORDER.size
        val indexB = TIMELINE_LABEL_ORDER.indexOf(b).takeIf { it >= 0 } ?: TIMELINE_LABEL_ORDER.size
        indexA.compareTo(indexB)
    }

    return sorted.map { (label, items) ->
        TimelineSection(
            label = label,
            screenshots = items,
            subLabel = computeTimelineSubLabel(label, items)
        )
    }
}

/**
 * Returns a timeline label based on the screenshot timestamp.
 * Categories (mutually exclusive):
 * - **Today** — taken today (since midnight local time)
 * - **Yesterday** — taken the previous day
 * - **This Week** — taken 2–6 days ago
 * - **Last Week** — taken 7–13 days ago
 * - **This Month** — taken 14+ days ago but still in the current calendar month
 * - **Older** — all previous months
 */
internal fun getTimelineLabel(timestamp: Long): String {
    val now = LocalDate.now()
    val screenshotDate = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    val daysDifference = ChronoUnit.DAYS.between(screenshotDate, now)

    return when {
        daysDifference == 0L -> "Today"
        daysDifference == 1L -> "Yesterday"
        daysDifference in 2..6 -> "This Week"
        daysDifference in 7..13 -> "Last Week"
        screenshotDate.monthValue == now.monthValue && screenshotDate.year == now.year -> "This Month"
        else -> "Older"
    }
}

/**
 * Returns a formatted sub-label (date range) for a timeline section.
 * Uses actual screenshot dates for accuracy in "This Month" and "Older" sections.
 */
internal fun computeTimelineSubLabel(label: String, screenshots: List<Screenshot>): String {
    val now = LocalDate.now(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM dd")

    return when (label) {
        "Today" -> now.format(formatter)
        "Yesterday" -> now.minusDays(1).format(formatter)
        "This Week" -> {
            val oldestDate = screenshots.maxOfOrNull { screenshot ->
                Instant.ofEpochMilli(screenshot.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toEpochDay()
            }?.let { LocalDate.ofEpochDay(it) } ?: now.minusDays(6)
            "${oldestDate.format(formatter)} - ${now.format(formatter)}"
        }
        "Last Week" -> {
            val start = now.minusDays(13)
            val end = now.minusDays(7)
            "${start.format(formatter)} - ${end.format(formatter)}"
        }
        "This Month", "Older" -> {
            val oldest = screenshots.minByOrNull { it.timestamp }?.let { screenshot ->
                Instant.ofEpochMilli(screenshot.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            } ?: now
            oldest.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
        else -> ""
    }
}
