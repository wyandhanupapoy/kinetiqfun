package org.example.kinetiqfun

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_menu)
        hideSystemUI()

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val rvGames = findViewById<RecyclerView>(R.id.rvGames)
        
        val games = listOf(
            GameItem(1, "Kesatria PCD", R.drawable.rock1),
            GameItem(2, "Dance Master", R.drawable.body1),
            GameItem(3, "Pose Fighter", R.drawable.head1)
        )

        val adapter = GameAdapter(games) { game ->
            val intent = when (game.id) {
                1 -> Intent(this, KesatriaPCDActivity::class.java)
                2 -> Intent(this, GameTwoActivity::class.java)
                3 -> Intent(this, GameThreeActivity::class.java)
                else -> null
            }
            intent?.let {
                startActivity(it)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        rvGames.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvGames.adapter = adapter

        // Add snapping for better user experience
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(rvGames)
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
