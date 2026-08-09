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

    private fun getModelUrl(lang: String): String = when (lang) {
        "ru" -> "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        "en" -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        else -> lang
    }

    override fun startListening(onResult: (String) -> Unit) {
        val engine = dataRepository.getSttEngine()

        // Audio focus request
        requestAudioFocus()

        if (engine == SttEngine.SYSTEM) {
            startSystemStt(onResult)
        } else {
            startVoskStt(onResult)
        }
    }

    private fun startSystemStt(onResult: (String) -> Unit) {
        stopListeningInternal()

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) onResult(matches[0])
                        abandonAudioFocus()
                    }
                    override fun onError(errorCode: Int) {
                        _isListening.value = false
                        _error.value = "SpeechRecognizer error: $errorCode"
                        abandonAudioFocus()
                    }
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
                speechRecognizer?.startListening(intent)
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
        stopListeningInternal()
        abandonAudioFocus()
    }

    private fun stopListeningInternal() {
        listeningJob?.cancel()
        listeningJob = null
        
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        _isListening.value = false
        _partialResults.value = ""

        wakeWordPlatform.stopListening()
    }

    private fun requestAudioFocus() {
        if (dataRepository.isWatchIntegrationEnabled()) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }

        // Pause music (Exclusive focus)
        val focusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
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
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAudioFocus() {
        if (dataRepository.isWatchIntegrationEnabled()) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
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
