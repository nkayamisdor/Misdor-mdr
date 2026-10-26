package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.VoiceLanguage
import com.example.data.model.VoicePreset
import com.example.data.model.VoiceSettings
import com.example.data.model.VoiceTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiceSynthesizer(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.FRENCH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.getDefault()
                }
                loadVoices()
                _isInitialized.value = true
                setupUtteranceListener()
            }
        } else {
            Log.e("VoiceSynthesizer", "TextToSpeech init failed with status: $status")
        }
    }

    private fun loadVoices() {
        try {
            val voices = tts?.voices?.toList() ?: emptyList()
            _availableVoices.value = voices
        } catch (e: Exception) {
            Log.w("VoiceSynthesizer", "Could not query TTS voices: ${e.message}")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId?.startsWith("preview_") == true) {
                    _isSpeaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("preview_") == true) {
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith("preview_") == true) {
                    _isSpeaking.value = false
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId?.startsWith("preview_") == true) {
                    _isSpeaking.value = false
                }
            }
        })
    }

    fun applySettings(settings: VoiceSettings) {
        val engine = tts ?: return

        // Set locale
        val locale = getSupportedLanguages().find { it.code == settings.languageCode }?.locale
            ?: Locale.FRENCH
        try {
            engine.language = locale
        } catch (e: Exception) {
            Log.w("VoiceSynthesizer", "Error setting language: ${e.message}")
        }

        // Set voice if specific voice selected
        if (settings.voiceName != null) {
            try {
                val voice = engine.voices?.find { it.name == settings.voiceName }
                if (voice != null) {
                    engine.voice = voice
                }
            } catch (e: Exception) {
                Log.w("VoiceSynthesizer", "Error setting voice: ${e.message}")
            }
        }

        // Apply Pitch with Tone intonation modifier
        val effectivePitch = (settings.pitch * (1.0f + settings.tone.pitchModifier))
            .coerceIn(0.25f, 2.0f)
        engine.setPitch(effectivePitch)

        // Apply Speed with Tone cadence modifier
        val effectiveSpeed = (settings.speed * (1.0f + settings.tone.speedModifier))
            .coerceIn(0.25f, 2.5f)
        engine.setSpeechRate(effectiveSpeed)
    }

    fun speakPreview(text: String, settings: VoiceSettings) {
        if (!_isInitialized.value) return
        stopPreview()
        applySettings(settings)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "preview_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopPreview() {
        tts?.stop()
        _isSpeaking.value = false
    }

    suspend fun synthesizeToAudioFile(
        text: String,
        settings: VoiceSettings,
        onProgressUpdate: (Float) -> Unit = {}
    ): Result<SynthesisResult> = suspendCancellableCoroutine { continuation ->
        val engine = tts
        if (engine == null || !_isInitialized.value) {
            continuation.resume(Result.failure(IllegalStateException("Moteur vocal non prêt. Veuillez patienter.")))
            return@suspendCancellableCoroutine
        }

        try {
            applySettings(settings)

            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            val sanitizedTitle = text.take(15)
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .trim('_')
                .ifEmpty { "voix" }
            val outputFile = File(audioDir, "${sanitizedTitle}_${System.currentTimeMillis()}.wav")

            val utteranceId = "file_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            onProgressUpdate(0.2f)

            // Register specific utterance listener for this file generation
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId) {
                        onProgressUpdate(0.5f)
                    }
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        onProgressUpdate(1.0f)
                        val durationMs = computeAudioDuration(outputFile)
                        val sizeFormatted = formatFileSize(outputFile.length())
                        setupUtteranceListener() // restore preview listener
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.success(
                                    SynthesisResult(
                                        file = outputFile,
                                        durationMs = durationMs,
                                        fileSizeBytes = outputFile.length(),
                                        fileSizeFormatted = sizeFormatted
                                    )
                                )
                            )
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        setupUtteranceListener()
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("Échec de la synthèse vocale"))
                            )
                        }
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) {
                        setupUtteranceListener()
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("Erreur de synthèse vocale (code $errorCode)"))
                            )
                        }
                    }
                }
            })

            val status = engine.synthesizeToFile(text, params, outputFile, utteranceId)
            if (status != TextToSpeech.SUCCESS) {
                setupUtteranceListener()
                if (continuation.isActive) {
                    continuation.resume(
                        Result.failure(Exception("Impossible de lancer la synthèse vocale vers le fichier"))
                    )
                }
            }

            continuation.invokeOnCancellation {
                engine.stop()
                setupUtteranceListener()
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }

        } catch (e: Exception) {
            setupUtteranceListener()
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    private fun computeAudioDuration(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 1000L
        } catch (e: Exception) {
            1000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 Ko"
            val kb = bytes / 1024.0
            return if (kb < 1024) {
                String.format(Locale.getDefault(), "%.1f Ko", kb)
            } else {
                String.format(Locale.getDefault(), "%.1f Mo", kb / 1024.0)
            }
        }

        fun getSupportedLanguages(): List<VoiceLanguage> {
            return listOf(
                VoiceLanguage("fr_FR", "Français (France)", "🇫🇷", Locale.FRANCE),
                VoiceLanguage("fr_CA", "Français (Canada)", "🇨🇦", Locale.CANADA_FRENCH),
                VoiceLanguage("en_US", "English (United States)", "🇺🇸", Locale.US),
                VoiceLanguage("en_GB", "English (United Kingdom)", "🇬🇧", Locale.UK),
                VoiceLanguage("es_ES", "Español (España)", "🇪🇸", Locale.forLanguageTag("es-ES")),
                VoiceLanguage("de_DE", "Deutsch (Deutschland)", "🇩🇪", Locale.GERMANY),
                VoiceLanguage("it_IT", "Italiano (Italia)", "🇮🇹", Locale.ITALY),
                VoiceLanguage("pt_BR", "Português (Brasil)", "🇧🇷", Locale.forLanguageTag("pt-BR"))
            )
        }

        fun getPresets(): List<VoicePreset> {
            return listOf(
                VoicePreset(
                    id = "studio_pro",
                    name = "Studio Pro",
                    subtitle = "Clair, équilibré et chaleureux",
                    iconEmoji = "🎙️",
                    pitch = 0.95f,
                    speed = 1.0f,
                    tone = VoiceTone.NEUTRAL,
                    sampleText = "Bienvenue dans Voix Studio. Votre texte sera transformé en une voix claire et professionnelle."
                ),
                VoicePreset(
                    id = "pub_radio",
                    name = "Pub & Énergique",
                    subtitle = "Dynamique et percutant",
                    iconEmoji = "⚡",
                    pitch = 1.15f,
                    speed = 1.2f,
                    tone = VoiceTone.DYNAMIC,
                    sampleText = "Offre exceptionnelle disponible dès aujourd'hui ! Ne manquez pas cette opportunité unique !"
                ),
                VoicePreset(
                    id = "robot",
                    name = "Cyborg Futuriste",
                    subtitle = "Intonation robotique de science-fiction",
                    iconEmoji = "🤖",
                    pitch = 0.65f,
                    speed = 0.85f,
                    tone = VoiceTone.ROBOTIC,
                    sampleText = "Initialisation du système audio terminée. Connexion neuronale prête pour la transmission vocale."
                ),
                VoicePreset(
                    id = "calm",
                    name = "Méditation & Douceur",
                    subtitle = "Rythme lent et apaisant",
                    iconEmoji = "🌸",
                    pitch = 0.90f,
                    speed = 0.75f,
                    tone = VoiceTone.CALM,
                    sampleText = "Respirez profondément, détendez chaque muscle de votre corps et laissez le calme vous envahir."
                ),
                VoicePreset(
                    id = "deep_cinema",
                    name = "Baryton Cinéma",
                    subtitle = "Voix grave et captivante",
                    iconEmoji = "🗿",
                    pitch = 0.45f,
                    speed = 0.90f,
                    tone = VoiceTone.DEEP,
                    sampleText = "Dans un monde où le silence régnait en maître, une nouvelle voix allait tout changer."
                ),
                VoicePreset(
                    id = "storyteller",
                    name = "Conte & Narration",
                    subtitle = "Expressif et mélodieux",
                    iconEmoji = "🧸",
                    pitch = 1.25f,
                    speed = 0.95f,
                    tone = VoiceTone.THEATRICAL,
                    sampleText = "Il était une fois, au cœur d'une forêt enchantée, un petit écureuil qui rêvait d'apprendre à parler aux étoiles."
                )
            )
        }
    }
}

data class SynthesisResult(
    val file: File,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String
)
