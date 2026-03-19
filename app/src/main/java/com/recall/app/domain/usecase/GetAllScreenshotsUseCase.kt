package com.recall.app.domain.usecase

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllScreenshotsUseCase @Inject constructor(
    private val screenshotRepository: ScreenshotRepository
) {
    operator fun invoke(): Flow<List<Screenshot>> {
        return screenshotRepository.getAllScreenshots()
    }
}
