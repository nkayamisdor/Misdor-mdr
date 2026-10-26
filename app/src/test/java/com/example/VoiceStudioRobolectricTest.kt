package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.VoiceSynthesizer
import com.example.data.local.VoiceClipDao
import com.example.data.local.VoiceStudioDatabase
import com.example.data.model.VoiceClip
import com.example.data.model.VoiceTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceStudioRobolectricTest {

    private lateinit var database: VoiceStudioDatabase
    private lateinit var voiceClipDao: VoiceClipDao
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VoiceStudioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        voiceClipDao = database.voiceClipDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun testAppNameResource() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Voix Studio", appName)
    }

    @Test
    fun testVoicePresetsAndLanguages() {
        val presets = VoiceSynthesizer.getPresets()
        assertTrue("Presets should not be empty", presets.isNotEmpty())
        assertTrue("Should include studio_pro preset", presets.any { it.id == "studio_pro" })
        assertTrue("Should include pub_radio preset", presets.any { it.id == "pub_radio" })
        assertTrue("Should include robot preset", presets.any { it.id == "robot" })

        val languages = VoiceSynthesizer.getSupportedLanguages()
        assertTrue("Languages should contain French", languages.any { it.code == "fr_FR" })
        assertTrue("Languages should contain English", languages.any { it.code == "en_US" })
    }

    @Test
    fun testVoiceToneModifiers() {
        val neutral = VoiceTone.NEUTRAL
        assertEquals(0.0f, neutral.pitchModifier, 0.001f)
        assertEquals(0.0f, neutral.speedModifier, 0.001f)

        val robotic = VoiceTone.ROBOTIC
        assertTrue("Robotic pitch should be lowered", robotic.pitchModifier < 0f)

        val theatrical = VoiceTone.THEATRICAL
        assertTrue("Theatrical pitch should be elevated", theatrical.pitchModifier > 0f)
    }

    @Test
    fun testAudioTimeFormatting() {
        val formattedZero = AudioPlayerManager.formatTime(0L)
        assertEquals("00:00", formattedZero)

        val formattedOneMinute = AudioPlayerManager.formatTime(65000L)
        assertEquals("01:05", formattedOneMinute)

        val formattedTenMinutes = AudioPlayerManager.formatTime(600000L)
        assertEquals("10:00", formattedTenMinutes)
    }

    @Test
    fun testFileSizeFormatting() {
        val zeroBytes = VoiceSynthesizer.formatFileSize(0L)
        assertEquals("0 Ko", zeroBytes)

        val oneKb = VoiceSynthesizer.formatFileSize(1024L)
        assertTrue(oneKb.contains("1.0 Ko") || oneKb.contains("1,0 Ko"))
    }

    @Test
    fun testRoomDatabaseInsertAndQuery() = runBlocking {
        val clip = VoiceClip(
            title = "Test Voix Pub",
            script = "Script pour la publicité",
            filePath = "/fake/path/audio.wav",
            durationMs = 5000L,
            fileSizeFormatted = "120 Ko",
            pitch = 1.15f,
            speed = 1.2f,
            tone = VoiceTone.DYNAMIC.displayName,
            language = "fr_FR",
            voiceName = "Standard",
            isFavorite = false
        )

        val id = voiceClipDao.insertClip(clip)
        assertTrue("Generated ID should be positive", id > 0)

        val retrieved = voiceClipDao.getClipById(id)
        assertNotNull(retrieved)
        assertEquals("Test Voix Pub", retrieved?.title)
        assertEquals(1.15f, retrieved?.pitch ?: 0f, 0.01f)
        assertFalse(retrieved?.isFavorite ?: true)

        // Toggle favorite
        voiceClipDao.updateFavorite(id, true)
        val favRetrieved = voiceClipDao.getClipById(id)
        assertTrue(favRetrieved?.isFavorite == true)

        // Delete clip
        voiceClipDao.deleteById(id)
        val deleted = voiceClipDao.getClipById(id)
        assertEquals(null, deleted)
    }
}
