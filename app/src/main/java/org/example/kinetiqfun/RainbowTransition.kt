package org.example.kinetiqfun

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator

class RainbowTransitionView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var progress = 0f // 0f to 1.5f
        set(value) {
            field = value
            invalidate()
        }
    
    // 0 = sweeping in (covering), 1 = sweeping out (revealing)
    var mode = 0 

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val colors = intArrayOf(
            Color.parseColor("#FF0000"), // Red
            Color.parseColor("#FF7F00"), // Orange
            Color.parseColor("#FFFF00"), // Yellow
            Color.parseColor("#00FF00"), // Green
            Color.parseColor("#0000FF"), // Blue
            Color.parseColor("#4B0082"), // Indigo
            Color.parseColor("#9400D3"), // Violet
            Color.TRANSPARENT
        )
        
        val gradientWidth = width * 0.8f 

        if (mode == 0) {
            // Covering screen: swipe from left to right
            val currentX = width * progress
            val startX = currentX
            val endX = currentX - gradientWidth
            
            paint.shader = LinearGradient(startX, 0f, endX, 0f, colors, null, Shader.TileMode.CLAMP)
            
            // Draw a solid rect of the last color (Red) behind the gradient to ensure screen is fully covered
            val solidPaint = Paint().apply { color = colors[0] }
            canvas.drawRect(0f, 0f, startX, height.toFloat(), solidPaint)
            
            // Draw the gradient sweeping edge
            canvas.drawRect(endX, 0f, startX, height.toFloat(), paint)
            
        } else {
            // Revealing screen: swipe from left to right (removing cover)
            val currentX = width * progress
            val startX = currentX
            val endX = currentX + gradientWidth
            
            val reverseColors = intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#9400D3"),
                Color.parseColor("#4B0082"),
                Color.parseColor("#0000FF"),
                Color.parseColor("#00FF00"),
                Color.parseColor("#FFFF00"),
                Color.parseColor("#FF7F00"),
                Color.parseColor("#FF0000")
            )
            paint.shader = LinearGradient(startX, 0f, endX, 0f, reverseColors, null, Shader.TileMode.CLAMP)
            
            // Draw a solid rect of Red on the remaining part of the screen
            val solidPaint = Paint().apply { color = reverseColors[reverseColors.size - 1] }
            canvas.drawRect(currentX, 0f, width.toFloat(), height.toFloat(), solidPaint)
            
            // Draw the gradient edge
            canvas.drawRect(startX, 0f, endX, height.toFloat(), paint)
        }
    }
}

object RainbowTransition {
    fun navigate(activity: Activity, intent: Intent, finishCurrent: Boolean = false) {
        SoundManager.playTransition()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = RainbowTransitionView(activity)
        view.mode = 0
        view.elevation = 9999f 
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        
        val animator = ValueAnimator.ofFloat(0f, 1.5f)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            view.progress = it.animatedValue as Float
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
                if (finishCurrent) {
                    activity.finish()
                    activity.overridePendingTransition(0, 0)
                }
            }
        })
        animator.start()
    }

    fun reveal(activity: Activity) {
        SoundManager.playTransition()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        
        // Hapus semua RainbowTransitionView lama agar tidak menyangkut saat kembali (back)
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is RainbowTransitionView) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { root.removeView(it) }

        val view = RainbowTransitionView(activity)
        view.mode = 1
        view.elevation = 9999f
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        
        val animator = ValueAnimator.ofFloat(0f, 1.5f)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            view.progress = it.animatedValue as Float
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                root.removeView(view)
            }
        })
        animator.start()
    }
}
