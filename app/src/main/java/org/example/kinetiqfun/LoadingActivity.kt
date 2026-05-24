package org.example.kinetiqfun

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.random.Random

class LoadingActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var txtLoading: TextView
    private lateinit var txtPercentage: TextView
    private lateinit var txtTips: TextView
    private lateinit var imgLogo: ImageView
    private lateinit var btnStart: android.widget.Button
    
    private var progressStatus = 0
    private val handler = Handler(Looper.getMainLooper())

    // Daftar Tips Game untuk pemain
    private val gameTips = arrayOf(
        "Tip: Posisikan tubuh Anda 2 meter dari kamera.",
        "Tip: Pastikan pencahayaan ruangan cukup terang.",
        "Tip: Gunakan pakaian yang kontras dengan latar belakang.",
        "Tip: Ikuti gerakan di layar seakurat mungkin!",
        "Tip: Ajak teman untuk melihat skormu!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_loading)
        hideSystemUI()

        // Enable and start background music when entering Loading screen
        (application as KinetiqFunApp).enableMusic()

        // Inisialisasi View
        progressBar = findViewById(R.id.progressBar)
        txtLoading = findViewById(R.id.txtLoading)
        txtPercentage = findViewById(R.id.txtPercentage)
        txtTips = findViewById(R.id.txtTips)
        imgLogo = findViewById(R.id.imgLogo)
        btnStart = findViewById(R.id.btnStart)

        // Tampilkan Tip Acak
        txtTips.text = gameTips[Random.nextInt(gameTips.size)]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.clipToOutline = true
        }

        btnStart.setOnClickListener {
            RainbowTransition.navigate(this, Intent(this, MenuActivity::class.java), finishCurrent = true)
        }

        setupAnimations()
        startLoading()
    }

    private fun setupAnimations() {
        // 1. Animasi Logo Berdenyut
        ObjectAnimator.ofPropertyValuesHolder(
            imgLogo,
            PropertyValuesHolder.ofFloat("scaleX", 1.05f),
            PropertyValuesHolder.ofFloat("scaleY", 1.05f)
        ).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }.start()

        // 2. Animasi Teks Loading (...)
        val loadingTexts = arrayOf("Loading", "Loading.", "Loading..", "Loading...")
        var textIndex = 0
        handler.post(object : Runnable {
            override fun run() {
                if (progressStatus < 100) {
                    txtLoading.text = loadingTexts[textIndex]
                    textIndex = (textIndex + 1) % loadingTexts.size
                    handler.postDelayed(this, 500)
                }
            }
        })
    }

    private fun startLoading() {
        Thread {
            while (progressStatus < 100) {
                progressStatus += 1
                handler.post {
                    progressBar.progress = progressStatus
                    txtPercentage.text = "$progressStatus%"
                }
                try {
                    Thread.sleep(35) 
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            handler.post {
                showStartButton()
            }
        }.start()
    }

    private fun showStartButton() {
        // Sembunyikan semua elemen loading
        findViewById<View>(R.id.progressBarBg).visibility = View.GONE
        progressBar.visibility = View.GONE
        txtLoading.visibility = View.GONE
        txtPercentage.visibility = View.GONE
        txtTips.visibility = View.GONE
        
        // Munculkan tombol Start dengan Fade In Simpel
        btnStart.visibility = View.VISIBLE
        btnStart.alpha = 0f
        btnStart.animate()
            .alpha(1f)
            .setDuration(500)
            .start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
