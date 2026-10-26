package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AudioWaveformPlayer
import com.example.ui.components.ScriptTemplatesDialog
import com.example.ui.components.VoiceParametersEditor
import com.example.ui.components.VoicePresetSelector
import com.example.ui.viewmodel.VoiceStudioViewModel

@Composable
fun StudioScreen(
    viewModel: VoiceStudioViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val scriptText by viewModel.scriptText.collectAsState()
    val audioTitle by viewModel.audioTitle.collectAsState()
    val voiceSettings by viewModel.voiceSettings.collectAsState()
    val selectedPresetId by viewModel.selectedPresetId.collectAsState()
    val isSynthesizing by viewModel.isSynthesizing.collectAsState()
    val synthesisProgress by viewModel.synthesisProgress.collectAsState()
    val currentClip by viewModel.currentClip.collectAsState()
    val isTtsReady by viewModel.synthesizer.isInitialized.collectAsState()
    val isSpeakingPreview by viewModel.synthesizer.isSpeaking.collectAsState()
    val playerState by viewModel.playerManager.playerState.collectAsState()

    var showTemplatesDialog by remember { mutableStateOf(false) }

    // Text stats
    val charCount = scriptText.length
    val wordCount = if (scriptText.isBlank()) 0 else scriptText.trim().split("\\s+".toRegex()).size
    val estimatedDurationSec = (wordCount * 0.45).toInt().coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("studio_screen")
    ) {
        // App Studio Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Voix Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Générateur & Éditeur Vocal Pro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Engine status badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isTtsReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isTtsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTtsReady) "Studio Prêt" else "Chargement...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTtsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Title Input Field
        OutlinedTextField(
            value = audioTitle,
            onValueChange = { viewModel.updateTitle(it) },
            label = { Text("Titre de l'enregistrement") },
            placeholder = { Text("Ex: Publicité Été 2026, Introduction...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audio_title_input"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Script Text Input with Action Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Texte à Générer",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showTemplatesDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("templates_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Modèles",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp
                        )
                    }

                    if (scriptText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.updateScript("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = scriptText,
                onValueChange = { viewModel.updateScript(it) },
                placeholder = { Text("Écrivez ou collez ici le texte à transformer en voix...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("script_text_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$charCount car. • $wordCount mots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "Durée estimée : ~$estimatedDurationSec s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Voices Carousel & Language Selection
        VoicePresetSelector(
            presets = viewModel.presets,
            selectedPresetId = selectedPresetId,
            onPresetSelected = { viewModel.applyPreset(it) },
            languages = viewModel.supportedLanguages,
            selectedLanguageCode = voiceSettings.languageCode,
            onLanguageSelected = { viewModel.updateLanguage(it) },
            availableVoiceCount = viewModel.synthesizer.availableVoices.collectAsState().value.size
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Voice Parameters Module (Pitch, Speed, Tone with dual sliders and numeric inputs)
        VoiceParametersEditor(
            settings = voiceSettings,
            isSpeakingPreview = isSpeakingPreview,
            onPitchChange = { viewModel.updatePitch(it) },
            onSpeedChange = { viewModel.updateSpeed(it) },
            onToneChange = { viewModel.updateTone(it) },
            onReset = { viewModel.resetParameters() },
            onPreviewClick = {
                if (isSpeakingPreview) {
                    viewModel.stopPreview()
                } else {
                    viewModel.previewSpeak()
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Generate Voice Button
        Button(
            onClick = { viewModel.generateAndSaveAudio() },
            enabled = !isSynthesizing && scriptText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("generate_voice_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isSynthesizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Génération de l'audio (${(synthesisProgress * 100).toInt()}%)...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Générer la Voix Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Audio Player Section (Appears when audio is generated or selected)
        AnimatedVisibility(
            visible = currentClip != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            currentClip?.let { clip ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lecteur & Téléchargement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AudioWaveformPlayer(
                        clip = clip,
                        playerState = playerState,
                        onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                        onSeek = { viewModel.playerManager.seekTo(it) },
                        onSpeedChange = { viewModel.playerManager.setPlaybackSpeed(it) },
                        onDownload = { viewModel.downloadClip(clip) },
                        onShare = { viewModel.shareClip(clip) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Templates & AI Style Dialog
    if (showTemplatesDialog) {
        ScriptTemplatesDialog(
            onDismiss = { showTemplatesDialog = false },
            onTemplateSelect = { template ->
                viewModel.applyScriptTemplate(template)
            },
            onEnhanceSelect = { style ->
                viewModel.enhanceScript(style)
            }
        )
    }
}
