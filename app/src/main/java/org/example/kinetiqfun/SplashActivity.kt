package org.example.kinetiqfun

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Agar konten bisa tampil di area "poni" (Notch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // 2. Buat window benar-benar fullscreen (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_splash)
        hideSystemUI()

        val videoView = findViewById<FullScreenVideoView>(R.id.videoView)
        val videoPath = "android.resource://" + packageName + "/" + R.raw.splash_video
        videoView.setVideoPath(videoPath)

        videoView.setOnPreparedListener {
            videoView.start()
        }

        videoView.setOnCompletionListener {
            goToLoadingActivity()
        }

        videoView.setOnErrorListener { _, _, _ ->
            goToLoadingActivity()
            true
        }
    }

    private fun goToLoadingActivity() {
        if (!isFinishing) {
            startActivity(Intent(this, LoadingActivity::class.java))
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Layar tetap menyala selama video diputar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
