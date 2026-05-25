package org.example.kinetiqfun

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build

class BalapGeolRenderer(private val view: OverlayView) : GameRenderer {

    override fun onDraw(canvas: Canvas) {
        // Disco Filter Effect
        view.discoHue = (view.discoHue + 3f) % 360f
        view.discoPaint.color = Color.HSVToColor(70, floatArrayOf(view.discoHue, 1f, 1f))
        canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), view.discoPaint)
        
        // Track Finish Line
        val finishLineY = 200f
        val startY = view.height - 150f
        val trackLength = startY - finishLineY
        
        // Garis Start dan Finish masing-masing player
        val p1CenterX = view.width * 0.25f
        val p2CenterX = view.width * 0.75f
        
        view.trackPaint.style = Paint.Style.STROKE
        // P1 Finish & Start
        canvas.drawLine(p1CenterX - 200f, finishLineY, p1CenterX + 200f, finishLineY, view.trackPaint)
        canvas.drawLine(p1CenterX - 200f, startY + 50f, p1CenterX + 200f, startY + 50f, view.trackPaint)
        
        // P2 Finish & Start
        canvas.drawLine(p2CenterX - 200f, finishLineY, p2CenterX + 200f, finishLineY, view.trackPaint)
        canvas.drawLine(p2CenterX - 200f, startY + 50f, p2CenterX + 200f, startY + 50f, view.trackPaint)
        
        view.trackPaint.textSize = 60f
        view.trackPaint.style = Paint.Style.FILL
        view.trackPaint.typeface = view.gameTypeface
        view.trackPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("FINISH", p1CenterX, finishLineY - 20f, view.trackPaint)
        canvas.drawText("FINISH", p2CenterX, finishLineY - 20f, view.trackPaint)
        
        view.p1Drawable?.let {
            val p1X = view.width * 0.25f
            val p1Y = startY - (view.balapP1Progress * trackLength)
            it.setBounds((p1X - 250f).toInt(), (p1Y - 250f).toInt(), (p1X + 250f).toInt(), (p1Y + 250f).toInt())
            it.draw(canvas)
        }
        
        view.p2Drawable?.let {
            val p2X = view.width * 0.75f
            val p2Y = startY - (view.balapP2Progress * trackLength)
            it.setBounds((p2X - 250f).toInt(), (p2Y - 250f).toInt(), (p2X + 250f).toInt(), (p2Y + 250f).toInt())
            it.draw(canvas)
        }
    }
}
