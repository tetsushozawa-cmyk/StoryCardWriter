package com.tetsushozawa.storycardwriter

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.tetsushozawa.storycardwriter.data.StoryData
import com.tetsushozawa.storycardwriter.data.StoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExternalScwOpenInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun actionViewWithContentUri_importsScwThroughExistingRepository() {
        val expected = StoryData(title = "外部ACTION_VIEW")
        val uri = StoryRepository.createShareUri(context, expected)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, StoryRepository.ScwMimeType)

        val result = ExternalScwIntentHandler.importFromViewIntent(context, intent)

        assertEquals(expected.title, result?.getOrThrow()?.title)
        assertEquals(expected.title, StoryRepository.load(context).title)
    }

    @Test
    fun nonViewIntent_doesNotImportExternalFile() {
        val current = StoryData(title = "編集中")
        StoryRepository.createNew(context, current)
        val uri = StoryRepository.createShareUri(context, StoryData(title = "読み込まない"))
        val intent = Intent(Intent.ACTION_MAIN).setDataAndType(uri, StoryRepository.ScwMimeType)

        val result = ExternalScwIntentHandler.importFromViewIntent(context, intent)

        assertNull(result)
        assertEquals(current.title, StoryRepository.load(context).title)
    }

    @Test
    fun brokenScw_returnsFailureAndPreservesCurrentStory() {
        val current = StoryData(title = "保持する編集中データ")
        StoryRepository.createNew(context, current)
        val file = File(context.cacheDir, "shared_stories/broken.scw").apply {
            parentFile?.mkdirs()
            writeText("{ broken json")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, StoryRepository.ScwMimeType)

        val result = ExternalScwIntentHandler.importFromViewIntent(context, intent)

        assertTrue(result?.isFailure == true)
        assertEquals(current.title, StoryRepository.load(context).title)
    }
}
