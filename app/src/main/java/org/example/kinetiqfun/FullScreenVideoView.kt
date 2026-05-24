package org.example.kinetiqfun

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class FullScreenVideoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Mengambil ukuran yang diminta oleh parent (dalam hal ini layar penuh)
        val width = getDefaultSize(0, widthMeasureSpec)
        val height = getDefaultSize(0, heightMeasureSpec)
        
        // Memaksa VideoView untuk berukuran tepat sebesar layar tanpa menjaga aspek rasio
        setMeasuredDimension(width, height)
    }
}
