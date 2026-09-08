package com.katya.app.stt

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.katya.app.data.DataRepository
import com.katya.app.data.SttEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

actual fun createSttController(): SttController = AndroidSttController()

class AndroidSttController : SttController {
    private val wakeWordPlatform: WakeWordPlatform by inject(WakeWordPlatform::class.java)
    private val dataRepository: DataRepository by inject(DataRepository::class.java)
    private val cloudSttEngine: CloudSttEngine by inject(CloudSttEngine::class.java)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var listeningJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResults = MutableStateFlow("")
    override val partialResults: StateFlow<String> = _partialResults.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val context: Context by inject(Context::class.java)
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        // Collect state from Vosk in case it is used
        scope.launch {
            wakeWordPlatform.isListeningToSpeech.collect {
                if (dataRepository.getSttEngine() != SttEngine.SYSTEM) {
                    _isListening.value = it
                }
            }
        }
        scope.launch {
            wakeWordPlatform.partialSttResults.collect {
                if (dataRepository.getSttEngine() != SttEngine.SYSTEM) {
                    _partialResults.value = it
                }
            }
        }
    }

    /** Language tag sent to the system recognizer. Defaults to the system language, falling back to ru-RU. */
    private fun recognitionLanguage(): String {
        val locale = android.content.res.Configuration().apply {
            setLocale(java.util.Locale.getDefault())
        }.locale.toLanguageTag()
        return if (locale.isBlank()) "ru-RU" else locale
    }

    /**
     * Ensures a usable [SpeechRecognizer] exists. Android's SpeechRecognizer is notoriously
     * stateful: creating a new instance while another is alive, or starting twice in a row,
     * yields ERROR_RECOGNIZER_BUSY / ERROR_CLIENT with no callback. We always tear down the
     * previous one (with a small async settle) before creating a fresh instance.
     */
    private fun freshSpeechRecognizer(): SpeechRecognizer {
        destroySpeechRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        // Give the platform a moment to release resources of the destroyed recognizer.
        // Without this, an immediate create/start on some OEM builds fails with ERROR_RECOGNIZER_BUSY.
        try {
            Thread.sleep(80)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        speechRecognizer = recognizer
        return recognizer
    }

    private fun destroySpeechRecognizer() {
        val old = speechRecognizer
        speechRecognizer = null
        if (old != null) {
            try {
                old.stopListening()
            } catch (_: Exception) {}
            try {
                old.destroy()
            } catch (_: Exception) {}
        }
    }

    /** Shared listener building block so SYSTEM and GKPSR don't drift. */
    private fun RecognitionListener.onSystemError(errorCode: Int) {
        when (errorCode) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            -> {
                // Transient — destroy and let the caller retry via a fresh recognizer.
                _isListening.value = false
                destroySpeechRecognizer()
                _error.value = "SpeechRecognizer busy ($errorCode), please retry"
            }

            else -> {
                _isListening.value = false
                _error.value = "SpeechRecognizer error: $errorCode"
            }
        }
        abandonAudioFocus()
    }

    private fun getModelUrl(lang: String): String = when (lang) {
        "ru" -> "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        "en" -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        else -> lang
    }

    override fun startListening(onResult: (String) -> Unit) {
        val engine = dataRepository.getSttEngine()

        // Audio focus request
        requestAudioFocus()

        when (engine) {
            SttEngine.SYSTEM -> startSystemStt(onResult)
            SttEngine.GKPSR -> startGkpsrStt(onResult)
            SttEngine.LOCAL -> startVoskStt(onResult)
            SttEngine.CLOUD -> startCloudStt(onResult)
        }
    }

    private fun startGkpsrStt(onResult: (String) -> Unit) {
        stopListeningInternal()

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch {
                val recognizer = freshSpeechRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) onResult(matches[0])
                        abandonAudioFocus()
                    }
                    override fun onError(errorCode: Int) = onSystemError(errorCode)
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _error.value = null
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partialResults.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer.startListening(intent)
            }
        } else {
            _error.value = "Speech recognition not available"
            abandonAudioFocus()
        }
    }

    private fun startSystemStt(onResult: (String) -> Unit) {
        stopListeningInternal()

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch {
                val recognizer = freshSpeechRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) onResult(matches[0])
                        abandonAudioFocus()
                    }
                    override fun onError(errorCode: Int) = onSystemError(errorCode)
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _error.value = null
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partialResults.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer.startListening(intent)
            }
        } else {
            _error.value = "Speech recognition not available"
            abandonAudioFocus()
        }
    }

    private fun startVoskStt(onResult: (String) -> Unit) {
        val modelLang = dataRepository.getWakeWordModelLang()
        val url = getModelUrl(modelLang)

        listeningJob?.cancel()
        listeningJob = scope.launch {
            wakeWordPlatform.finalSttResults.collect { result ->
                onResult(result)
                abandonAudioFocus()
            }
        }

        wakeWordPlatform.startSpeechRecognition(url)
    }

    override fun stopListening() {
        if (dataRepository.getSttEngine() == SttEngine.CLOUD && cloudSttEngine.isRecording) {
            finishCloudStt()
        } else {
            stopListeningInternal()
            abandonAudioFocus()
        }
    }

    private var cloudResultCallback: ((String) -> Unit)? = null

    /**
     * Push-to-talk cloud recognition: opening the mic starts a capture whose
     * duration is controlled by the user tapping the mic again — [stopListening]
     * finishes the capture and uploads it to the configured transcriptions API.
     */
    private fun startCloudStt(onResult: (String) -> Unit) {
        stopListeningInternal()
        _isListening.value = true
        _partialResults.value = ""
        _error.value = null
        cloudResultCallback = onResult
        cloudSttEngine.startRecording()
    }

    private fun finishCloudStt() {
        val callback = cloudResultCallback ?: return
        cloudResultCallback = null
        _isListening.value = false
        scope.launch {
            val result = cloudSttEngine.stopAndTranscribe()
            result.fold(
                onSuccess = { text ->
                    _partialResults.value = ""
                    if (text.isNotBlank()) callback(text)
                },
                onFailure = { e -> _error.value = e.message },
            )
            abandonAudioFocus()
        }
    }

    private fun stopListeningInternal() {
        listeningJob?.cancel()
        listeningJob = null

        destroySpeechRecognizer()

        _isListening.value = false
        _partialResults.value = ""

        wakeWordPlatform.stopListening()
    }

    private fun requestAudioFocus() {
        if (dataRepository.isWatchIntegrationEnabled()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                val hasBluetoothMic = devices.any { 
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (hasBluetoothMic) {
                    audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            } else {
                audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        }

        // Pause music (Exclusive focus)
        val focusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
        } else {
            null
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (dataRepository.isWatchIntegrationEnabled()) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
        }

        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(null)

        // Restart WakeWord if it is enabled
        if (dataRepository.isWakeWordEnabled()) {
            val modelLang = dataRepository.getWakeWordModelLang()
            val url = getModelUrl(modelLang)
            val trigger = dataRepository.getWakeWordTrigger()
            wakeWordPlatform.startListening(url, trigger)
        }
    }
}
