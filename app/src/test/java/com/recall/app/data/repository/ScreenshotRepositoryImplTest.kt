package com.recall.app.data.repository

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [Build.VERSION_CODES.Q])
class ScreenshotRepositoryImplTest {

    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var ocrProcessor: OcrProcessor
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var repository: ScreenshotRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        screenshotDao = mock()
        ocrProcessor = mock()
        embeddingGenerator = mock()
        repository = ScreenshotRepositoryImpl(screenshotDao, ocrProcessor, embeddingGenerator, context)
    }

    @Test
    fun `scanExistingScreenshots with empty cursor returns 0`() = runTest {
        // On Android 10 (Q), no runtime permission is needed for MediaStore
        // Without adding shadows, Robolectric's default ContentResolver provides an empty cursor
        // for MediaStore queries unless specifically populated.
        val count = repository.scanExistingScreenshots()
        assertEquals(0, count)
    }
}
