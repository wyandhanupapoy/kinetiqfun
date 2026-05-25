package org.example.kinetiqfun

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max

class KesatriaRenderer(private val view: OverlayView) : GameRenderer {

    override fun onDraw(canvas: Canvas) {
        val scale = max(view.width.toFloat() / view.imageWidth, view.height.toFloat() / view.imageHeight)
        val canvasOffsetX = (view.width - view.imageWidth * scale) / 2f
        val canvasOffsetY = (view.height - view.imageHeight * scale) / 2f

        view.hpPaint.typeface = view.gameTypeface

        view.playersPose.forEach { (id, data) ->
            val (_, pXOffset) = data
            val tx = { x: Float -> 
                var sx = (x + pXOffset) * scale + canvasOffsetX
                if (view.isFrontCamera) sx = view.width - sx
                sx
            }
            val ty = { y: Float -> y * scale + canvasOffsetY }
            val ls = view.getPos(id, PoseLandmark.LEFT_SHOULDER)
            val rs = view.getPos(id, PoseLandmark.RIGHT_SHOULDER)
            if (ls != null && rs != null) {
                val sw = Math.hypot((tx(ls.x) - tx(rs.x)).toDouble(), (ty(ls.y) - ty(rs.y)).toDouble()).toFloat()
                view.drawBody(canvas, id, tx, ty, sw * 1.55f)
                view.drawShoulders(canvas, id, tx, ty, sw)
                view.drawHands(canvas, id, tx, ty, sw * 0.48f)
                view.drawHead(canvas, id, tx, ty, sw * 0.95f)
            }
        }

        view.rocks.forEach { rock ->
            if (!rock.isDestroyed) {
                // Flash white based on health (getting redder/damaged)
                val currentHP = 5 - rock.hits
                canvas.drawText("HP: $currentHP", rock.rect.centerX(), rock.rect.top - 10f, view.hpPaint)

                val shakeX = if (rock.shakeAmount > 0) (Math.random().toFloat() - 0.5f) * rock.shakeAmount else 0f
                val shakeY = if (rock.shakeAmount > 0) (Math.random().toFloat() - 0.5f) * rock.shakeAmount else 0f
                
                view.tempRect.set(rock.rect)
                view.tempRect.offset(shakeX, shakeY)
                
                // Draw Rock (Box)
                canvas.drawBitmap(view.boxBitmap, null, view.tempRect, null)
                
                // Draw damage cracks (overlay) if hit
                if (rock.hits > 0) {
                    view.commonPaint.style = Paint.Style.STROKE
                    view.commonPaint.strokeWidth = 5f
                    view.commonPaint.color = Color.argb(180, 0, 0, 0) // Black cracks
                    
                    // Simple procedural cracks
                    for (i in 0 until rock.hits) {
                        val angle = (i * 1.5f)
                        canvas.drawLine(
                            view.tempRect.centerX(), view.tempRect.centerY(),
                            view.tempRect.centerX() + Math.cos(angle.toDouble()).toFloat() * (view.tempRect.width()/2),
                            view.tempRect.centerY() + Math.sin(angle.toDouble()).toFloat() * (view.tempRect.height()/2),
                            view.commonPaint
                        )
                    }
                }
                
                if (rock.shakeAmount > 0) rock.shakeAmount -= 2.5f
            }
        }
    }
}
