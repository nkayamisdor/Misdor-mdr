package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class VoiceTone(
    val displayName: String,
    val description: String,
    val pitchModifier: Float,
    val speedModifier: Float
) {
    NEUTRAL("Neutre", "Équilibré et naturel pour tout type d'usage", 0.0f, 0.0f),
    DYNAMIC("Dynamique", "Énergique, idéal pour publicités et vidéos", 0.15f, 0.15f),
    CALM("Posé & Calme", "Doux et apaisant, idéal pour méditation et audiobooks", -0.1f, -0.15f),
    THEATRICAL("Théâtral", "Expressif et captivant pour les histoires et contes", 0.25f, -0.05f),
    DEEP("Baryton Grave", "Voix posée, autoritaire et cinématographique", -0.35f, -0.1f),
    ROBOTIC("Robotique", "Cadence cybernétique et intonation futuriste", -0.2f, -0.1f)
}

data class VoiceLanguage(
    val code: String,
    val displayName: String,
    val flag: String,
    val locale: Locale
)

data class VoicePreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconEmoji: String,
    val pitch: Float,
    val speed: Float,
    val tone: VoiceTone,
    val sampleText: String
)

data class VoiceSettings(
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val tone: VoiceTone = VoiceTone.NEUTRAL,
    val languageCode: String = "fr_FR",
    val voiceName: String? = null
)

@Entity(tableName = "voice_clips")
data class VoiceClip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val script: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeFormatted: String,
    val pitch: Float,
    val speed: Float,
    val tone: String,
    val language: String,
    val voiceName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
