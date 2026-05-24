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

        val btnInfo = findViewById<android.widget.Button>(R.id.btnInfo)
        btnInfo.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_developer_info, null)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
                
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            
            dialogView.findViewById<android.widget.Button>(R.id.btnCloseDialog).setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val rvGames = findViewById<RecyclerView>(R.id.rvGames)
        
        val games = listOf(
            GameItem(1, "Kesatria PCD", null),
            GameItem(2, "Dance Master", null),
            GameItem(3, "Pose Fighter", null)
        )

        val adapter = GameAdapter(games) { game ->
            val intent = when (game.id) {
                1 -> Intent(this, KesatriaPCDActivity::class.java)
                2 -> Intent(this, GameTwoActivity::class.java)
                3 -> Intent(this, GameThreeActivity::class.java)
                else -> null
            }
            intent?.let {
                RainbowTransition.navigate(this, it, finishCurrent = false)
            }
        }

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvGames.layoutManager = layoutManager
        rvGames.adapter = adapter

        // Add snapping for better user experience
        val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
        snapHelper.attachToRecyclerView(rvGames)

        // Add Carousel Effect
        rvGames.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val centerX = recyclerView.width / 2f
                val maxScale = 1.0f
                val minScale = 0.8f
                
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val childCenterX = (child.left + child.right) / 2f
                    val distanceFromCenter = Math.abs(centerX - childCenterX)
                    val maxDistance = recyclerView.width / 2f
                    
                    val scale = maxScale - (distanceFromCenter / maxDistance) * (maxScale - minScale)
                    val clampedScale = Math.max(minScale, Math.min(scale, maxScale))
                    
                    child.scaleX = clampedScale
                    child.scaleY = clampedScale
                    
                    // Control the blur overlay alpha based on distance from center
                    val blurOverlay = child.findViewById<android.view.View>(R.id.blurOverlay)
                    val blurAlpha = (distanceFromCenter / (maxDistance * 0.5f))
                    val clampedBlurAlpha = Math.max(0f, Math.min(1f, blurAlpha))
                    if (blurOverlay != null) {
                        blurOverlay.alpha = clampedBlurAlpha
                    }
                    
                    // True Blur Effect for Android 12+ (API 31)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (clampedBlurAlpha > 0.05f) {
                            val blurRadius = clampedBlurAlpha * 25f // Max blur radius 25
                            child.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP))
                        } else {
                            child.setRenderEffect(null)
                        }
                    }
                }
            }
        })
        
        // Setup proper dynamic padding to strictly center the first and last items
        rvGames.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rvGames.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val child = rvGames.layoutManager?.getChildAt(0)
                if (child != null) {
                    val padding = (rvGames.width - child.width) / 2
                    rvGames.setPadding(padding, rvGames.paddingTop, padding, rvGames.paddingBottom)
                    
                    // Force the recycler view to apply the padding
                    rvGames.post {
                        rvGames.scrollToPosition(0)
                        rvGames.scrollBy(1, 0)
                        rvGames.scrollBy(-1, 0)
                    }
                }
            }
        })
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
