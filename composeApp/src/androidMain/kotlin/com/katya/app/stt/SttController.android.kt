package com.katya.app.stt

import com.katya.app.data.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

actual fun createSttController(): SttController = AndroidSttController()

class AndroidSttController : SttController {
    private val wakeWordPlatform: WakeWordPlatform by inject(WakeWordPlatform::class.java)
    private val dataRepository: DataRepository by inject(DataRepository::class.java)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var listeningJob: Job? = null

    override val isListening: StateFlow<Boolean> = wakeWordPlatform.isListeningToSpeech
    override val partialResults: StateFlow<String> = wakeWordPlatform.partialSttResults
    
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    private val context: android.content.Context by inject(android.content.Context::class.java)
    private val audioManager: android.media.AudioManager by lazy {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }

    private fun getModelUrl(lang: String): String = when (lang) {
        "ru" -> "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        "en" -> "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        else -> lang
    }

    override fun startListening(onResult: (String) -> Unit) {
        val modelLang = dataRepository.getWakeWordModelLang()
        val url = getModelUrl(modelLang)
        
        // Start Bluetooth SCO if a headset is connected
        if (dataRepository.isWatchIntegrationEnabled()) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }

        listeningJob?.cancel()
        listeningJob = scope.launch {
            // Subscribe to final results
            wakeWordPlatform.finalSttResults.collect { result ->
                onResult(result)
            }
        }
        
        wakeWordPlatform.startSpeechRecognition(url)
    }

    override fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        wakeWordPlatform.stopListening()
        
        if (dataRepository.isWatchIntegrationEnabled()) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        }
        
        // Restart WakeWord if it is enabled
        if (dataRepository.isWakeWordEnabled()) {
            val modelLang = dataRepository.getWakeWordModelLang()
            val url = getModelUrl(modelLang)
            val trigger = dataRepository.getWakeWordTrigger()
            wakeWordPlatform.startListening(url, trigger)
        }
    }
}
