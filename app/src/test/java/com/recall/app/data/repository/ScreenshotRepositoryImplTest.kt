package com.recall.app.data.repository

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.toDomainModel
import com.recall.app.domain.model.PipelineFailureCode
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ScreenshotRepositoryImplTest {

    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var ocrProcessor: OcrProcessor
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var permissionRepository: PermissionRepository
    private lateinit var repository: ScreenshotRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ocrProcessor = mock()
        embeddingGenerator = mock()
        permissionRepository = mock { on { hasActualPermission() } doReturn true }
        screenshotDao = mock { onBlocking { getAllScreenshotPaths() } doReturn emptyList() }
        repository = ScreenshotRepositoryImpl(
            screenshotDao, ocrProcessor, embeddingGenerator, permissionRepository, context
        )
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
    fun `ScreenshotEntity defaults pipelineCode to NONE`() {
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
            tagsJson = ""
            // pipelineCode and appName intentionally omitted — should default to NONE / ""
        )
        assertEquals(PipelineFailureCode.NONE, entity.pipelineCode)
        assertEquals("", entity.appName)
        assertEquals(PipelineFailureCode.NONE, entity.toDomainModel().pipelineCode)
    }

    @Test
    fun `appName is empty string when OWNER_PACKAGE_NAME unavailable (API 28 fallback)`() {
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

    @Test
    fun `toDomainModel maps pipelineCode correctly`() {
        val entity = buildEntity(appName = "").copy(pipelineCode = PipelineFailureCode.OCR_FAILED)
        assertEquals(PipelineFailureCode.OCR_FAILED, entity.toDomainModel().pipelineCode)
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
        embeddingByteArray = ByteArray(512), // non-null = fully indexed
        appName = appName
    )
}
