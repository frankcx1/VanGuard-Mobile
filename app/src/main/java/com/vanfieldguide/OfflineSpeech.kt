package com.vanfieldguide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback

/** Main-thread only; never creates a generic network-capable recognizer. */
class OfflineSpeech(
    private val context: Context,
    private val report: (String, Boolean) -> Unit,
    private val transcript: (String) -> Unit,
) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { cancel("Listening timed out. You can type or try again.") }
    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    private fun service(): SpeechRecognizer {
        check(SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { "On-device speech unavailable. Use typed input." }
        return recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
            recognizer = it
            it.setRecognitionListener(this)
        }
    }

    fun preflight() = safely {
        service().checkRecognitionSupport(request(), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                report("Installed on-device languages: ${support.installedOnDeviceLanguages.joinToString().ifBlank { "none reported" }}. A disconnected speech test is still required.", false)
            }
            override fun onError(error: Int) { report("Speech preflight could not complete (code $error). Offline status is unverified.", false) }
        })
    }

    fun prepare() = safely {
        service().triggerModelDownload(request())
        report("Requested English speech resources from the system service. Check resources again after preparation.", false)
    }

    fun start() = safely {
        service().startListening(request())
        report("Listening on device…", true)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 20_000)
    }
    fun stop() = safely {
        recognizer?.stopListening()
        report("Finishing transcription…", true)
    }
    fun cancel(message: String = "Listening canceled.") {
        handler.removeCallbacks(timeout)
        recognizer?.cancel()
        report(message, false)
    }
    fun close() {
        handler.removeCallbacks(timeout)
        recognizer?.destroy()
        recognizer = null
    }
    private fun safely(block: () -> Unit) {
        try { block() } catch (error: Exception) { report(error.message ?: "Speech unavailable. Use typed input.", false) }
    }
    override fun onResults(results: Bundle?) {
        handler.removeCallbacks(timeout)
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!text.isNullOrBlank()) transcript(text)
        report(if (text.isNullOrBlank()) "No transcript returned. Try again or type." else "Review the transcript, especially model names and fault codes.", false)
    }
    override fun onError(error: Int) {
        handler.removeCallbacks(timeout)
        report("Speech did not complete (code $error). Use typed input or check speech resources.", false)
    }
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { report("Finishing transcription…", true) }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
