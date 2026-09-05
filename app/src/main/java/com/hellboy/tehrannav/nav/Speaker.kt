package com.hellboy.tehrannav.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** Persian text-to-speech for navigation voice guidance. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            Log.e("Speaker", "TTS init failed: $status")
            return
        }
        val res = tts?.setLanguage(Locale("fa", "IR"))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            // fall back to default locale so voice guidance still works
            tts?.setLanguage(Locale.getDefault())
        }
    }

    fun speak(text: String, enabled: Boolean) {
        if (!enabled || !ready) return
        // stop previous utterance to keep guidance crisp
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}