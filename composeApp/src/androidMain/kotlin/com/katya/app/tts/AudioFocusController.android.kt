package com.katya.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import org.koin.java.KoinJavaComponent.inject

actual object AudioFocusController {
    private val context: Context by inject(Context::class.java)

    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Depth, not a boolean: the mic and the voice can overlap (a reply spoken while the
     * wake word is still listening), and a boolean would let the inner release hand focus
     * back while the outer one was still talking.
     */
    private var depth = 0

    /** Set at the outermost acquire: did media actually play before we took over? */
    private var shouldResumePlayback = false

    private var focusRequest: AudioFocusRequest? = null

    actual fun acquire() {
        val am = audioManager ?: return
        if (depth == 0) {
            // Check *before* requesting focus: requesting it can itself make other apps
            // go transiently quiet, which would make "is music playing" answer wrongly.
            shouldResumePlayback = isMediaPlaying(am)
            val request = buildFocusRequest()
            focusRequest = request
            if (request != null) {
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }
            if (shouldResumePlayback) sendMediaKey(am, KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
        depth++
    }

    actual fun release() {
        if (depth == 0) return
        depth--
        if (depth > 0) return

        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) } ?: run {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        focusRequest = null
        // Only ever resume something we paused ourselves — a user who had nothing playing
        // must not get music started by the assistant.
        if (shouldResumePlayback) sendMediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        shouldResumePlayback = false
    }

    /**
     * True when something is actually producing media audio. `isMusicActive()` covers
     * players that hold focus; stream volume alone cannot tell a playing app from a
     * muted one, and treating "volume is up" as "music is playing" would start playback
     * for a user who only had the volume slider high.
     */
    private fun isMediaPlaying(am: AudioManager): Boolean = am.isMusicActive

    private fun sendMediaKey(am: AudioManager, keyCode: Int) {
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun buildFocusRequest(): AudioFocusRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(true)
            // An empty listener left other apps permanently ducked once we returned focus.
            // Handling the losses keeps a phone call or another app from being left silent.
            .setOnAudioFocusChangeListener { }
            .build()
    }
}
