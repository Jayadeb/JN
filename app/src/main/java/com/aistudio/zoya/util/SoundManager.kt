package com.aistudio.zoya.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager(context: Context) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)

    fun playActivationSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun playDeactivationSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 150)
    }

    fun playErrorSound() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 200)
    }

    fun release() {
        toneGenerator.release()
    }
}
