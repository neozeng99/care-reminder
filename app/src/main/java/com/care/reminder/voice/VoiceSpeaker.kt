package com.care.reminder.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 适老化中文语音播报 + 响铃震动辅助器
 * 语速放缓至 0.88 倍，确保老年人听得清楚
 */
object VoiceSpeaker {

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false
    private var pendingText: String? = null
    private var activeRingtone: Ringtone? = null

    fun speak(context: Context, text: String, vibrate: Boolean = true) {
        val appContext = context.applicationContext
        if (vibrate) {
            triggerVibration(appContext)
        }

        if (tts == null) {
            pendingText = text
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val result = engine.setLanguage(Locale.CHINA)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.CHINESE)
                    }
                    engine.setSpeechRate(0.88f)
                    engine.setPitch(1.0f)
                    isReady = true
                    pendingText?.let { queued ->
                        pendingText = null
                        speakInternal(appContext, queued)
                    }
                } else {
                    playFallbackRingtone(appContext)
                }
            }
        } else if (isReady) {
            speakInternal(appContext, text)
        } else {
            pendingText = text
        }
    }

    private fun speakInternal(context: Context, text: String) {
        val engine = tts
        if (engine == null) {
            playFallbackRingtone(context)
            return
        }
        val utteranceId = "care_tts_${System.currentTimeMillis()}"
        // 播报两遍，防止老人第一遍没听清
        val repeatedText = "$text。再提醒一遍：$text"
        val code = engine.speak(repeatedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (code == TextToSpeech.ERROR) {
            playFallbackRingtone(context)
        }
    }

    fun playFallbackRingtone(context: Context) {
        try {
            stopRingtone()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            activeRingtone = ringtone
            ringtone.play()
        } catch (_: Exception) {
        }
    }

    fun triggerVibration(context: Context) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            val pattern = longArrayOf(0, 500, 250, 500, 250, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    fun stopAll() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        stopRingtone()
    }

    private fun stopRingtone() {
        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
            }
            activeRingtone = null
        } catch (_: Exception) {
        }
    }
}
