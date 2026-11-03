package com.example.audio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Dedicated Intent-based Voice Note Exporter for sending finished 48kHz Mono recordings
 * directly to WhatsApp, Telegram, Google Chat, or Character.AI as native audio voice notes.
 *
 * Configures:
 * - WhatsApp PTT naming (`PTT-YYYYMMDD-WAxxxx.opus`) & `audio/ogg; codecs=opus` MIME type
 * - Telegram Ogg/Opus (`.ogg`) 48kHz Mono voice bubble format
 * - Google Chat native audio stream attachment with `ClipData` URI grant
 */
object VoiceNoteIntentExporter {
    private const val TAG = "VoiceNoteIntentExporter"

    private val CANDIDATE_PACKAGES = mapOf(
        MessengerTarget.WHATSAPP to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        ),
        MessengerTarget.TELEGRAM to listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram"
        ),
        MessengerTarget.GOOGLE_CHAT to listOf(
            "com.google.android.apps.dynamite",
            "com.google.android.gm"
        ),
        MessengerTarget.CHARACTER_AI to listOf(
            "ai.character.app"
        )
    )

    /**
     * Builds a ready-to-launch `Intent.ACTION_SEND` configured with `ClipData`, `EXTRA_STREAM`,
     * and `FLAG_GRANT_READ_URI_PERMISSION` so external messengers recognize the payload as a
     * recorded voice note stream.
     */
    fun buildVoiceNoteExportIntent(
        context: Context,
        audioFile: File,
        messenger: MessengerTarget
    ): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            audioFile
        )

        val mimeType = when (messenger) {
            MessengerTarget.WHATSAPP -> "audio/ogg; codecs=opus"
            MessengerTarget.TELEGRAM -> "audio/ogg"
            MessengerTarget.GOOGLE_CHAT -> "audio/ogg"
            MessengerTarget.CHARACTER_AI -> "audio/wav"
        }

        val resolvedPackage = resolveInstalledMessengerPackage(context, messenger)

        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TITLE, audioFile.name)
            clipData = ClipData.newRawUri(audioFile.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (resolvedPackage != null) {
                setPackage(resolvedPackage)
            }
        }
    }

    /**
     * Dispatches a finished voice note file to the requested external app (WhatsApp, Telegram,
     * Google Chat, or Character.AI), falling back gracefully to the system share sheet if the
     * target app is not installed on the current device/emulator.
     */
    fun exportVoiceNoteToApp(
        context: Context,
        audioFile: File,
        messenger: MessengerTarget
    ): Boolean {
        if (!audioFile.exists()) {
            Toast.makeText(context, "Voice note file not found", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val directIntent = buildVoiceNoteExportIntent(context, audioFile, messenger)
            val pm = context.packageManager

            if (directIntent.`package` != null && directIntent.resolveActivity(pm) != null) {
                context.startActivity(directIntent)
                Toast.makeText(
                    context,
                    "Sending ${audioFile.name} (${messenger.badgeText}) to ${messenger.label}...",
                    Toast.LENGTH_SHORT
                ).show()
                true
            } else {
                // Fallback to universal chooser with ClipData & URI read grant
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    audioFile
                )
                val chooserTarget = Intent(Intent.ACTION_SEND).apply {
                    type = messenger.preferredContainer.mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TITLE, audioFile.name)
                    clipData = ClipData.newRawUri(audioFile.name, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(
                    chooserTarget,
                    "Send 48kHz Voice Note (${audioFile.name}) to ${messenger.label}"
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                Toast.makeText(
                    context,
                    "Prepared ${audioFile.name} (${messenger.badgeText}) for ${messenger.label}",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export voice note via Intent to ${messenger.label}", e)
            Toast.makeText(
                context,
                "Ready at ${audioFile.name} (${messenger.badgeText})",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    private fun resolveInstalledMessengerPackage(
        context: Context,
        messenger: MessengerTarget
    ): String? {
        val pm = context.packageManager
        val candidates = CANDIDATE_PACKAGES[messenger] ?: listOf(messenger.packageName)
        for (pkg in candidates) {
            val probe = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                setPackage(pkg)
            }
            if (probe.resolveActivity(pm) != null) {
                return pkg
            }
        }
        return null
    }
}
