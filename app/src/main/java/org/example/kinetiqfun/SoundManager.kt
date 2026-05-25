package org.example.kinetiqfun

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundManager {
    private var soundPool: SoundPool? = null
    private var clickSoundId = 0
    private var transitionSoundId = 0

    fun init(context: Context) {
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attributes).build()
        
        val clickId = context.resources.getIdentifier("button_click_sfx", "raw", context.packageName)
        if (clickId != 0) clickSoundId = soundPool?.load(context, clickId, 1) ?: 0

        val transitionId = context.resources.getIdentifier("transition_sfx", "raw", context.packageName)
        if (transitionId != 0) transitionSoundId = soundPool?.load(context, transitionId, 1) ?: 0
    }

    fun playClick() {
        if (clickSoundId != 0) soundPool?.play(clickSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playTransition() {
        if (transitionSoundId != 0) soundPool?.play(transitionSoundId, 1f, 1f, 1, 0, 1f)
    }
}
