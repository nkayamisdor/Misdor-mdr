package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DownloadExportHelper {

    /**
     * Copies the generated audio file to the device's public Downloads folder.
     */
    fun downloadToDevice(
        context: Context,
        sourceFilePath: String,
        desiredTitle: String
    ): Result<String> {
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            return Result.failure(Exception("Le fichier audio source n'existe pas."))
        }

        val sanitizedTitle = desiredTitle
            .replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            .trim()
            .replace("\\s+".toRegex(), "_")
            .ifEmpty { "VoixStudio_${System.currentTimeMillis()}" }

        val fileName = "$sanitizedTitle.wav"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/VoixStudio"
                    )
                }

                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    return Result.failure(Exception("Impossible de créer l'entrée dans Téléchargements."))
                }

                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return Result.failure(Exception("Échec de l'écriture du fichier audio."))

                val savedPath = "Téléchargements/VoixStudio/$fileName"
                Toast.makeText(context, "✅ Téléchargé : $savedPath", Toast.LENGTH_LONG).show()
                Result.success(savedPath)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "VoixStudio"
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val destFile = File(downloadsDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, "✅ Téléchargé dans ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                Result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e("DownloadExportHelper", "Download failed: ${e.message}", e)
            Toast.makeText(context, "Erreur de téléchargement: ${e.message}", Toast.LENGTH_LONG).show()
            Result.failure(e)
        }
    }

    /**
     * Shares the audio file with other apps (WhatsApp, Telegram, Drive, Email, etc.)
     */
    fun shareAudioFile(context: Context, filePath: String, title: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Fichier audio introuvable.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Écoutez cet extrait généré avec Voix Studio : $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Partager la voix studio")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("DownloadExportHelper", "Share failed: ${e.message}", e)
            Toast.makeText(context, "Impossible de partager l'audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
