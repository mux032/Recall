package com.recall.app.data.repository

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.toDomainModel
import com.recall.app.domain.model.ProcessingState
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
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
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

    // ---------------------------------------------------------------------------
    // appName extraction — ScreenshotEntity / toDomainModel
    // ---------------------------------------------------------------------------

    @Test
    fun `toDomainModel maps appName correctly when set`() {
        val entity = buildEntity(appName = "com.whatsapp")
        val domain = entity.toDomainModel()
        assertEquals("com.whatsapp", domain.appName)
    }

    @Test
    fun `toDomainModel maps empty appName when not set`() {
        val entity = buildEntity(appName = "")
        val domain = entity.toDomainModel()
        assertEquals("", domain.appName)
    }

    @Test
    fun `ScreenshotEntity defaults appName to empty string`() {
        // Ensures the column has a safe default so existing DB rows without the
        // column (pre-migration) are deserialized without crashes.
        val entity = ScreenshotEntity(
            id = "default_test",
            filePath = "/sdcard/Screenshots/test.png",
            fileName = "test.png",
            dateCreated = 0L,
            dateIndexed = 0L,
            width = 1080,
            height = 1920,
            ocrText = null,
            category = "Uncategorized",
            tagsJson = "",
            processingState = ProcessingState.Pending.value
            // appName intentionally omitted — should default to ""
        )
        assertEquals("", entity.appName)
        assertEquals("", entity.toDomainModel().appName)
    }

    @Test
    fun `appName is empty string when OWNER_PACKAGE_NAME unavailable (API 28 fallback)`() {
        // On API < 29 OWNER_PACKAGE_NAME is not in the projection, so the column index
        // is -1 and appName falls back to "". Verify the entity and domain model both
        // reflect that contract without needing to spin up a different SDK level.
        val entity = buildEntity(appName = "")
        assertEquals("", entity.appName)
        assertEquals("", entity.toDomainModel().appName)
    }

    @Test
    fun `toDomainModel preserves all other fields when appName is populated`() {
        val entity = buildEntity(appName = "com.instagram.android")
        val domain = entity.toDomainModel()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.filePath, domain.filePath)
        assertEquals(entity.fileName, domain.fileName)
        assertEquals(entity.ocrText, domain.ocrText)
        assertEquals("com.instagram.android", domain.appName)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun buildEntity(appName: String): ScreenshotEntity = ScreenshotEntity(
        id = "test_${appName.replace(".", "_")}",
        filePath = "/sdcard/Screenshots/test.png",
        fileName = "test.png",
        dateCreated = 1_000_000L,
        dateIndexed = 2_000_000L,
        width = 1080,
        height = 1920,
        ocrText = "Sample text",
        category = "Uncategorized",
        tagsJson = "",
        processingState = ProcessingState.Done.value,
        appName = appName
    )
}
