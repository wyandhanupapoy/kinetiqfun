package org.example.kinetiqfun

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class SnowBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backgroundBitmap: Bitmap? = null
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    
    private data class Snowflake(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedX: Float,
        var speedY: Float,
        var alpha: Int
    )
    
    private val snowflakes = mutableListOf<Snowflake>()
    private val MAX_SNOWFLAKES = 120
    private var isInitialized = false

    init {
        // Load the background
        val options = BitmapFactory.Options()
        options.inScaled = false
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.loading_bg, options)
    }
    
    private fun initSnowflakes(width: Int, height: Int) {
        snowflakes.clear()
        for (i in 0 until MAX_SNOWFLAKES) {
            snowflakes.add(createRandomSnowflake(width, height, true))
        }
        isInitialized = true
    }
    
    private fun createRandomSnowflake(width: Int, height: Int, randomY: Boolean = false): Snowflake {
        val yPos = if (randomY) Random.nextFloat() * height else -20f
        return Snowflake(
            x = Random.nextFloat() * width,
            y = yPos,
            radius = Random.nextFloat() * 4f + 1.5f,
            speedX = Random.nextFloat() * 2f - 1f, // Slight wind left or right
            speedY = Random.nextFloat() * 2f + 1f, // Falling speed
            alpha = Random.nextInt(155) + 100 // 100 to 255 alpha
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = backgroundBitmap
        
        if (bitmap != null) {
            val scale = Math.max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val left = (width - scaledWidth) / 2f
            val top = (height - scaledHeight) / 2f
            
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            matrix.postTranslate(left, top)
            
            canvas.drawBitmap(bitmap, matrix, bgPaint)
        }
        
        if (width > 0 && height > 0) {
            if (!isInitialized) {
                initSnowflakes(width, height)
            }
            
            // Draw and update snowflakes
            for (i in snowflakes.indices) {
                val flake = snowflakes[i]
                
                snowPaint.alpha = flake.alpha
                canvas.drawCircle(flake.x, flake.y, flake.radius, snowPaint)
                
                flake.y += flake.speedY
                flake.x += flake.speedX
                
                // Make the snowflake sway sideways like real snow (sine wave)
                flake.x += (Math.sin(flake.y / 30.0) * 0.8).toFloat()
                
                // Reset if it goes out of bounds at the bottom or sides
                if (flake.y > height + flake.radius || flake.x < -flake.radius - 20f || flake.x > width + flake.radius + 20f) {
                    snowflakes[i] = createRandomSnowflake(width, height, false)
                }
            }
            
            // Trigger 60FPS continuous loop
            postInvalidateOnAnimation()
        }
    }
}
