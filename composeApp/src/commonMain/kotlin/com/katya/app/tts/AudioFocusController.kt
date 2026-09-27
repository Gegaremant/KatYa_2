package com.katya.app.tts

/**
 * Takes Android audio focus for as long as Katya is talking or listening, pauses whatever
 * the user had playing, and hands playback back afterwards.
 *
 * Feedback: "проверим, что приложение при озвучке и при включении микрофона сначала
 * забирает аудиофокус, ставит на паузу плеер, и в конце возвращает аудиофокус и включает
 * воспроизведение обратно. Но это только если до этого музыка играла, а если не играла,
 * то не запускать".
 *
 * Deliberately *not* a volume change: writing to STREAM_MUSIC rewrites the user's own
 * volume setting, and getting the restore wrong leaves their music quietly muted. Audio
 * focus plus a media key event leaves the volume slider alone and only affects playback.
 *
 * The "only if it was playing" rule is the important half. Without it, a user who had
 * nothing playing would finish a reply with music suddenly starting — the one behaviour
 * that makes an assistant feel broken.
 */
expect object AudioFocusController {
    /**
     * Claim focus and, if media was playing, pause it.
     *
     * Nesting-safe: the listener and the TTS engine may overlap, and only the outermost
     * [release] gives focus back.
     */
    fun acquire()

    /** Give focus back and resume playback if — and only if — this caller's [acquire] paused it. */
    fun release()
}
