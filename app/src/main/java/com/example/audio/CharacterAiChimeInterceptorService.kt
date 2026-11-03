package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * Production Android Foreground Service that maintains the Character.AI Audio Stream Output
 * Chime Interceptor ([ChimeStreamInterceptor]) during active voice calls and external
 * Character.AI sessions.
 *
 * Lifecycle & Responsibilities:
 * - Promotes itself to a Foreground Service (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`) with a
 *   low-importance status notification and quick "Stop Chime Blocker" action.
 * - Configures [AudioManager.MODE_IN_COMMUNICATION] and attaches [DynamicsProcessing] / [Equalizer]
 *   spectral notch and fast-attack transient limiter DSP to the active audio output session.
 * - Updates [ChimeStreamInterceptor.globalStats] so the Compose UI reflects live service telemetry.
 */
class CharacterAiChimeInterceptorService : Service() {
    companion object {
        private const val TAG = "ChimeInterceptorSvc"
        const val CHANNEL_ID = "opus_cai_chime_interceptor_channel"
        const val NOTIFICATION_ID = 4801

        const val ACTION_START_INTERCEPTOR = "com.example.audio.action.START_CHIME_INTERCEPTOR"
        const val ACTION_STOP_INTERCEPTOR = "com.example.audio.action.STOP_CHIME_INTERCEPTOR"
        const val EXTRA_AUDIO_SESSION_ID = "extra_audio_session_id"

        fun startService(context: Context, audioSessionId: Int = 0) {
            try {
                val intent = Intent(context, CharacterAiChimeInterceptorService::class.java).apply {
                    action = ACTION_START_INTERCEPTOR
                    putExtra(EXTRA_AUDIO_SESSION_ID, audioSessionId)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Fallback for unit-test / restricted background contexts
                try {
                    val fallbackIntent = Intent(context, CharacterAiChimeInterceptorService::class.java).apply {
                        action = ACTION_START_INTERCEPTOR
                        putExtra(EXTRA_AUDIO_SESSION_ID, audioSessionId)
                    }
                    context.startService(fallbackIntent)
                } catch (inner: Exception) {
                    Log.w(TAG, "Could not start CharacterAiChimeInterceptorService: ${inner.message}")
                }
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, CharacterAiChimeInterceptorService::class.java).apply {
                    action = ACTION_STOP_INTERCEPTOR
                }
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop CharacterAiChimeInterceptorService: ${e.message}")
            }
        }
    }

    private val interceptor = ChimeStreamInterceptor()

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_INTERCEPTOR -> {
                interceptor.releaseOutputEffects()
                ChimeStreamInterceptor.updateGlobalStats {
                    it.copy(
                        isServiceRunning = false,
                        currentGateState = "STANDBY • INTERCEPTOR PAUSED"
                    )
                }
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val sessionId = intent?.getIntExtra(EXTRA_AUDIO_SESSION_ID, 0) ?: 0
                promoteToForeground(sessionId)

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    try {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    } catch (ignored: Exception) {}
                }

                interceptor.attachToAudioOutputSession(sessionId)
                ChimeStreamInterceptor.updateGlobalStats {
                    it.copy(
                        isServiceRunning = true,
                        isInterceptorActive = true,
                        activeAudioSessionId = sessionId,
                        currentGateState = "FOREGROUND DSP ACTIVE • BLOCKING C.AI CHIMES"
                    )
                }
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(audioSessionId: Int) {
        try {
            val notification = buildForegroundNotification(audioSessionId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val fgsType = if (hasMicPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground promotion fallback: ${e.message}")
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (ignored: Exception) {}
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Character.AI Chime Interceptor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time 48kHz audio DSP chime suppression for Character.AI voice calls"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(audioSessionId: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CharacterAiChimeInterceptorService::class.java).apply {
            action = ACTION_STOP_INTERCEPTOR
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cfg = ChimeStreamInterceptor.globalConfig.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("OpusVoice • c.ai Chime Blocker Active")
            .setContentText(
                "Session $audioSessionId • Holdoff Gate ${cfg.postSpeechMuteWindowMs}ms • 1.55kHz Notch Armed"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Blocker",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        interceptor.releaseOutputEffects()
        stopForegroundCompat()
        ChimeStreamInterceptor.updateGlobalStats {
            it.copy(
                isServiceRunning = false,
                currentGateState = "STANDBY"
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
