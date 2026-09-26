package com.katya.app.tts

/**
 * Feedback #12: lower the user's media volume while Katya speaks and restore it after.
 *
 * Putting this in the speech engines rather than in the UI call sites means every voice —
 * system TTS, cloud and Piper — ducks the music, and no future call site can forget to.
 */
expect object AudioDuckingController {
    /** Lower the media volume for the duration of an utterance. */
    fun duck()

    /** Restore whatever the media volume was before [duck]. */
    fun unduck()
}
