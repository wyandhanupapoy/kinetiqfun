package org.example.kinetiqfun

import android.app.Activity
import android.app.Application
import android.media.MediaPlayer
import android.os.Bundle

class KinetiqFunApp : Application(), Application.ActivityLifecycleCallbacks {

    private var mediaPlayer: MediaPlayer? = null
    private var startedActivitiesCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        
        // Setup Media Player
        mediaPlayer = MediaPlayer.create(this, R.raw.background_music)
        mediaPlayer?.isLooping = true
    }

    private var isMusicEnabled = false

    private fun startMusic() {
        if (!isMusicEnabled) return
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    fun enableMusic() {
        isMusicEnabled = true
        if (startedActivitiesCount > 0) {
            startMusic()
        }
    }

    private fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivitiesCount == 0) {
            // App came to foreground
            startMusic()
        }
        startedActivitiesCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivitiesCount--
        if (startedActivitiesCount == 0) {
            // App went to background
            pauseMusic()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {
        RainbowTransition.reveal(activity)
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
