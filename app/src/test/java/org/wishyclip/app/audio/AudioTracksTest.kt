package org.wishyclip.app.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AudioTracksTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testAudioClipDataClass() {
        val clip = AudioClip(id = 1L, uri = "file:///dummy.m4a", startFrame = 5, volume = 0.8f)
        assertEquals(1L, clip.id)
        assertEquals("file:///dummy.m4a", clip.uri)
        assertEquals(5, clip.startFrame)
        assertEquals(0.8f, clip.volume, 0.01f)
    }

    @Test
    fun testWaveformExtractorFallback() {
        val dummyFile = File(context.cacheDir, "test.m4a")
        dummyFile.writeText("dummy content")

        val samples = WaveformExtractor.extractWaveform(dummyFile, samplesCount = 10)
        assertNotNull(samples)
        assertEquals(10, samples.size)
    }
}
