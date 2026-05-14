package com.recall.app.domain.model

/**
 * Represents the active filter applied to the HomeScreen screenshot grid.
 *
 * - [ALL]       — No filter; shows every screenshot (default).
 * - [RECENT]    — Shows only screenshots captured in the last 7 days.
 * - [BY_APP]    — Groups/filters by source app. Full support pending Phase 8 CategoryClassifier.
 * - [SUMMARIZED]— Shows only screenshots that have an AI-generated description.
 *                 Full support pending Phase 7 AI summary backend.
 */
enum class ScreenshotFilter {
    ALL,
    RECENT,
    BY_APP,
    SUMMARIZED
}
