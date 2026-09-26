package com.katya.app.tts

import android.content.Context
import android.media.AudioManager
import org.koin.java.KoinJavaComponent.inject

/**
 * Feedback #12: lower the user's music while Katya is talking, then put it back.
 *
 * Without this the greeting and every spoken reply were mixed straight into whatever the user
 * was listening to, so the app talked over the music and the user turned the volume down to
 * compensate — which then made the voice too quiet as well.
 *
 * The volume is only ever *lowered*, and the previous value is restored on completion, so an
 * interrupted utterance can never leave the media stream muted. The restore also backs off if
 * the user moved the volume themselves mid-utterance: their choice wins over ours.
 */
actual object AudioDuckingController {
    private val context: Context by inject(Context::class.java)

    private const val DUCK_FACTOR = 0.25f

    @Volatile
    private var savedMusicVolume: Int? = null

    private fun duckedVolume(from: Int): Int = (from * DUCK_FACTOR).toInt().coerceAtLeast(1)

    actual fun duck() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current <= 0) return
        if (savedMusicVolume == null) savedMusicVolume = current
        val target = duckedVolume(current)
        if (target < current) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        }
    }

    actual fun unduck() {
        val saved = savedMusicVolume ?: return
        savedMusicVolume = null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current <= duckedVolume(saved)) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0) }
        }
    }
}
