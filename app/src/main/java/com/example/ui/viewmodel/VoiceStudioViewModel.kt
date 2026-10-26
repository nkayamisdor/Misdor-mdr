package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.DownloadExportHelper
import com.example.audio.VoiceSynthesizer
import com.example.data.ai.ScriptTemplate
import com.example.data.ai.ScriptTemplates
import com.example.data.local.VoiceStudioDatabase
import com.example.data.model.VoiceClip
import com.example.data.model.VoiceLanguage
import com.example.data.model.VoicePreset
import com.example.data.model.VoiceSettings
import com.example.data.model.VoiceTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class StudioTab(val label: String, val iconEmoji: String) {
    STUDIO("Studio Vocal", "🎙️"),
    LIBRARY("Mes Enregistrements", "📂")
}

class VoiceStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VoiceStudioDatabase.getDatabase(application)
    private val voiceClipDao = database.voiceClipDao()

    val synthesizer = VoiceSynthesizer(application)
    val playerManager = AudioPlayerManager(application)

    private val _currentTab = MutableStateFlow(StudioTab.STUDIO)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    private val _scriptText = MutableStateFlow(
        "Bienvenue dans Voix Studio ! Tapez votre texte, choisissez une voix, ajustez la hauteur, la vitesse et l'intonation, puis téléchargez votre audio."
    )
    val scriptText: StateFlow<String> = _scriptText.asStateFlow()

    private val _audioTitle = MutableStateFlow("Voix Off Démo")
    val audioTitle: StateFlow<String> = _audioTitle.asStateFlow()

    private val _voiceSettings = MutableStateFlow(VoiceSettings())
    val voiceSettings: StateFlow<VoiceSettings> = _voiceSettings.asStateFlow()

    private val _selectedPresetId = MutableStateFlow<String?>("studio_pro")
    val selectedPresetId: StateFlow<String?> = _selectedPresetId.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private val _synthesisProgress = MutableStateFlow(0f)
    val synthesisProgress: StateFlow<Float> = _synthesisProgress.asStateFlow()

    private val _currentClip = MutableStateFlow<VoiceClip?>(null)
    val currentClip: StateFlow<VoiceClip?> = _currentClip.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Library search & filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    val allClips: StateFlow<List<VoiceClip>> = combine(
        voiceClipDao.getAllClips(),
        _searchQuery,
        _onlyFavorites
    ) { clips, query, favoritesOnly ->
        clips.filter { clip ->
            val matchesQuery = query.isEmpty() ||
                clip.title.contains(query, ignoreCase = true) ||
                clip.script.contains(query, ignoreCase = true) ||
                clip.tone.contains(query, ignoreCase = true) ||
                clip.voiceName.contains(query, ignoreCase = true)
            val matchesFav = !favoritesOnly || clip.isFavorite
            matchesQuery && matchesFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supportedLanguages: List<VoiceLanguage> = VoiceSynthesizer.getSupportedLanguages()
    val presets: List<VoicePreset> = VoiceSynthesizer.getPresets()

    fun setTab(tab: StudioTab) {
        _currentTab.value = tab
    }

    fun updateScript(text: String) {
        _scriptText.value = text
    }

    fun updateTitle(title: String) {
        _audioTitle.value = title
    }

    fun updatePitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.25f, 2.0f)
        _voiceSettings.value = _voiceSettings.value.copy(pitch = clamped)
        _selectedPresetId.value = null
    }

    fun updateSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2.5f)
        _voiceSettings.value = _voiceSettings.value.copy(speed = clamped)
        _selectedPresetId.value = null
    }

    fun updateTone(tone: VoiceTone) {
        _voiceSettings.value = _voiceSettings.value.copy(tone = tone)
        _selectedPresetId.value = null
    }

    fun updateLanguage(languageCode: String) {
        _voiceSettings.value = _voiceSettings.value.copy(
            languageCode = languageCode,
            voiceName = null
        )
        synthesizer.applySettings(_voiceSettings.value)
    }

    fun updateVoiceName(voiceName: String?) {
        _voiceSettings.value = _voiceSettings.value.copy(voiceName = voiceName)
        synthesizer.applySettings(_voiceSettings.value)
    }

    fun applyPreset(preset: VoicePreset) {
        _selectedPresetId.value = preset.id
        _voiceSettings.value = _voiceSettings.value.copy(
            pitch = preset.pitch,
            speed = preset.speed,
            tone = preset.tone
        )
        synthesizer.applySettings(_voiceSettings.value)
        _userMessage.value = "Preset « ${preset.name} » appliqué"
    }

    fun resetParameters() {
        _voiceSettings.value = VoiceSettings(
            pitch = 1.0f,
            speed = 1.0f,
            tone = VoiceTone.NEUTRAL,
            languageCode = _voiceSettings.value.languageCode,
            voiceName = null
        )
        _selectedPresetId.value = "studio_pro"
        _userMessage.value = "Paramètres réinitialisés par défaut"
    }

    fun previewSpeak() {
        val text = _scriptText.value.trim()
        if (text.isEmpty()) {
            _userMessage.value = "Veuillez entrer du texte à écouter."
            return
        }
        playerManager.pause()
        synthesizer.speakPreview(text, _voiceSettings.value)
    }

    fun stopPreview() {
        synthesizer.stopPreview()
    }

    fun generateAndSaveAudio() {
        val text = _scriptText.value.trim()
        if (text.isEmpty()) {
            _userMessage.value = "Veuillez saisir un texte avant de générer."
            return
        }

        viewModelScope.launch {
            _isSynthesizing.value = true
            _synthesisProgress.value = 0.1f

            val result = synthesizer.synthesizeToAudioFile(
                text = text,
                settings = _voiceSettings.value,
                onProgressUpdate = { progress ->
                    _synthesisProgress.value = progress
                }
            )

            _isSynthesizing.value = false

            result.onSuccess { synthesis ->
                val title = _audioTitle.value.trim().ifEmpty {
                    text.take(20).trim() + "..."
                }

                val clip = VoiceClip(
                    title = title,
                    script = text,
                    filePath = synthesis.file.absolutePath,
                    durationMs = synthesis.durationMs,
                    fileSizeFormatted = synthesis.fileSizeFormatted,
                    pitch = _voiceSettings.value.pitch,
                    speed = _voiceSettings.value.speed,
                    tone = _voiceSettings.value.tone.displayName,
                    language = _voiceSettings.value.languageCode,
                    voiceName = _voiceSettings.value.voiceName ?: "Standard"
                )

                val insertedId = voiceClipDao.insertClip(clip)
                val savedClip = clip.copy(id = insertedId)
                _currentClip.value = savedClip

                // Automatically load and start playback
                playerManager.loadAudio(savedClip.filePath, autoPlay = true)
                _userMessage.value = "✨ Voix générée avec succès !"
            }.onFailure { error ->
                _userMessage.value = "Erreur: ${error.message}"
            }
        }
    }

    fun playClip(clip: VoiceClip) {
        _currentClip.value = clip
        playerManager.loadAudio(clip.filePath, autoPlay = true)
    }

    fun downloadCurrentClip() {
        val clip = _currentClip.value
        if (clip == null) {
            _userMessage.value = "Aucun audio généré à télécharger."
            return
        }
        downloadClip(clip)
    }

    fun downloadClip(clip: VoiceClip) {
        val result = DownloadExportHelper.downloadToDevice(
            context = getApplication(),
            sourceFilePath = clip.filePath,
            desiredTitle = clip.title
        )
        result.onSuccess { path ->
            _userMessage.value = "Fichier exporté dans $path"
        }.onFailure { err ->
            _userMessage.value = "Échec du téléchargement: ${err.message}"
        }
    }

    fun shareClip(clip: VoiceClip) {
        DownloadExportHelper.shareAudioFile(
            context = getApplication(),
            filePath = clip.filePath,
            title = clip.title
        )
    }

    fun toggleFavorite(clip: VoiceClip) {
        viewModelScope.launch {
            voiceClipDao.updateFavorite(clip.id, !clip.isFavorite)
        }
    }

    fun deleteClip(clip: VoiceClip) {
        viewModelScope.launch {
            if (_currentClip.value?.id == clip.id) {
                playerManager.stop()
                _currentClip.value = null
            }
            voiceClipDao.deleteClip(clip)
            try {
                val file = File(clip.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
            _userMessage.value = "Enregistrement supprimé"
        }
    }

    fun applyScriptTemplate(template: ScriptTemplate) {
        _audioTitle.value = template.title
        _scriptText.value = template.defaultText
        _userMessage.value = "Modèle « ${template.title} » inséré !"
    }

    fun enhanceScript(style: String) {
        val current = _scriptText.value
        val enhanced = ScriptTemplates.enhanceScriptLocally(current, style)
        _scriptText.value = enhanced
        _userMessage.value = "Script optimisé pour le style sélectionné !"
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        synthesizer.shutdown()
    }
}
