package org.example.kinetiqfun

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.segmentation.SegmentationMask
import java.util.*
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    companion object Config {
        // SKALA (1.0f = ukuran normal bawaan)
        var headScale = 1.3f
        var bodyScale = 1.2f
        var shoulderScale = 1.2f
        var handScale = 1.1f

        // OFFSET POSISI X (Kiri/Kanan) dan Y (Atas/Bawah)
        var headOffsetX = 0f
        var headOffsetY = 0f
        
        var bodyOffsetX = 0f
        var bodyOffsetY = 0f
        
        var leftShoulderOffsetX = 0f
        var leftShoulderOffsetY = 0f
        
        var rightShoulderOffsetX = 0f
        var rightShoulderOffsetY = 0f
        
        var leftHandOffsetX = 0f
        var leftHandOffsetY = 0f
        
        var rightHandOffsetX = 0f
        var rightHandOffsetY = 0f
    }

    private val playersPose = mutableMapOf<Int, Pair<Pose, Float>>()
    private val playersMask = mutableMapOf<Int, SegmentationMask>()
    
    private val playerMaskPixels = mutableMapOf<Int, IntArray>()
    private val playerMaskBitmaps = mutableMapOf<Int, Bitmap>()
    private val gameTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(context, R.font.game_font)
    }

    private val targetLandmarks = mutableMapOf<Int, MutableMap<Int, PointF>>()
    private val drawnLandmarks = mutableMapOf<Int, MutableMap<Int, PointF>>()

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var isFrontCamera: Boolean = true

    private val handLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_left)
    private val handRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_right)
    private val head1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head1)
    private val head2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head2)
    private val body1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body1)
    private val body2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body2)
    
    private val shoulder1LeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder1_left)
    private val shoulder1RightBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder1_right)
    private val shoulder2LeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder2_left)
    private val shoulder2RightBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder2_right)

    private val groundBitmap = BitmapFactory.decodeResource(resources, R.drawable.ground)
    private val boxBitmap = BitmapFactory.decodeResource(resources, R.drawable.box)

    data class Rock(
        val id: Int,
        val ownerId: Int,
        var hits: Int = 0,
        val rect: RectF,
        var isDestroyed: Boolean = false,
        var velocityY: Float = 0f,
        var shakeAmount: Float = 0f
    )

    enum class GameMode { KESATRIA, BALAP_GEOL, TIRU_GAYA, NONE }
    var currentGameMode = GameMode.KESATRIA

    private var balapP1Progress = 0f
    private var balapP2Progress = 0f
    
    private var p1Drawable: Drawable? = null
    private var p2Drawable: Drawable? = null
    
    // Disco Filter
    private var discoHue = 0f
    private val discoPaint = Paint().apply { style = Paint.Style.FILL }

    private var rocks = mutableListOf<Rock>()
    private var nameP1 = ""
    private var nameP2 = ""
    private var scoreP1 = 0
    private var scoreP2 = 0
    private var winner: String? = null
    private var winSoundPlayed = false

    private var currentTargetPoseName = "IDLE"
    private var p1MatchProgress = 0f
    private var p2MatchProgress = 0f

    data class Projectile(var x: Float, var y: Float, val vx: Float, val ownerId: Int)
    private val projectiles = mutableListOf<Projectile>()
    private var hpP1 = 100
    private var hpP2 = 100

    private var gameStartTime = System.currentTimeMillis()

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val size: Float, var alpha: Int,
        val color: Int, var life: Int
    )
    private val particles = mutableListOf<Particle>()
    private val random = java.util.Random()

    // Floating Text for Hits
    data class FloatingText(
        var x: Float, var y: Float,
        val text: String, var alpha: Float, var life: Int
    )
    private val floatingTexts = mutableListOf<FloatingText>()

    // Screen Shake
    private var shakeIntensity = 0f

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        isAntiAlias = true
    }

    fun setGameMode(mode: GameMode) {
        currentGameMode = mode
        postInvalidate()
    }

    fun setWinner(winName: String) {
        this.winner = winName
        spawnVictoryParticles()
        postInvalidateOnAnimation()
    }

    fun resetGame() {
        this.winner = null
        this.winSoundPlayed = false
        this.particles.clear()
        postInvalidate()
    }

    fun updateGameState(fallingRocks: List<Rock>, scoreP1: Int, scoreP2: Int, nP1: String, nP2: String, win: String?) {
        if (currentGameMode != GameMode.KESATRIA) {
            gameStartTime = System.currentTimeMillis()
            winSoundPlayed = false
        }
        currentGameMode = GameMode.KESATRIA
        
        // Deep copy rocks to avoid concurrent modification and reference sharing bugs
        this.rocks = fallingRocks.map { it.copy(rect = RectF(it.rect)) }.toMutableList()
        
        this.scoreP1 = scoreP1
        this.scoreP2 = scoreP2
        this.nameP1 = nP1
        this.nameP2 = nP2
        if (win != null && this.winner == null) {
            spawnVictoryParticles()
        }
        this.winner = win
        postInvalidateOnAnimation()
    }
    
    fun onRockHit(rect: RectF) {
        spawnHitSparks(rect)
        floatingTexts.add(FloatingText(rect.centerX(), rect.top, "HIT!", 255f, 30))
        postInvalidateOnAnimation()
    }
    
    fun onRockDestroyed(rect: RectF) {
        spawnRockParticles(rect)
        shakeIntensity = 15f
        floatingTexts.add(FloatingText(rect.centerX(), rect.centerY(), "BROKEN!", 255f, 40))
        postInvalidateOnAnimation()
    }

    private fun spawnRockParticles(rect: RectF) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        for (i in 0..25) {
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = (random.nextFloat() - 0.5f) * 30f,
                vy = (random.nextFloat() - 0.5f) * 30f - 10f,
                size = random.nextFloat() * 25f + 10f,
                alpha = 255,
                color = if (random.nextBoolean()) Color.DKGRAY else Color.GRAY,
                life = 40 + random.nextInt(20)
            ))
        }
    }

    private fun spawnHitSparks(rect: RectF) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        for (i in 0..8) {
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = (random.nextFloat() - 0.5f) * 15f,
                vy = (random.nextFloat() - 0.5f) * 15f - 5f,
                size = random.nextFloat() * 8f + 4f,
                alpha = 255,
                color = Color.rgb(255, 165, 0), // Orange sparks
                life = 15 + random.nextInt(10)
            ))
        }
    }

    fun updateBalapGeolState(p1: Float, p2: Float, name1: String, name2: String, win: String?) {
        currentGameMode = GameMode.BALAP_GEOL
        balapP1Progress = p1
        balapP2Progress = p2
        nameP1 = name1
        nameP2 = name2
        winner = win
        postInvalidate()
    }


    private fun spawnHitParticles(x: Float, y: Float, color: Int) {
        for (i in 0..10) {
            particles.add(Particle(
                x = x,
                y = y,
                vx = (random.nextFloat() - 0.5f) * 30f,
                vy = (random.nextFloat() - 0.5f) * 30f,
                size = random.nextFloat() * 15f + 5f,
                alpha = 255,
                color = color,
                life = 20 + random.nextInt(15)
            ))
        }
    }

    private fun spawnVictoryParticles() {
        for (i in 0 until 100) {
            val vx = (random.nextFloat() - 0.5f) * 20f
            val vy = -random.nextFloat() * 30f
            val color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            particles.add(Particle(width / 2f, height / 2f, vx, vy, 10f + random.nextFloat() * 20f, 255, color, 60 + random.nextInt(40)))
        }
    }

    fun setResults(playerId: Int, pose: Pose?, mask: SegmentationMask?, width: Int, height: Int, isFront: Boolean, xOffset: Float = 0f) {
        if (pose != null && pose.allPoseLandmarks.size > 15) {
            playersPose[playerId] = Pair(pose, xOffset)
            if (mask != null) playersMask[playerId] = mask
            
            // Simpan target kamera langsung secara mentah (real-time)
            val playerTarget = targetLandmarks.getOrPut(playerId) { mutableMapOf() }
            pose.allPoseLandmarks.forEach { landmark ->
                val type = landmark.landmarkType
                val pos = playerTarget.getOrPut(type) { PointF() }
                pos.x = landmark.position.x
                pos.y = landmark.position.y
            }
        } else {
            playersPose.remove(playerId)
            targetLandmarks.remove(playerId)
            drawnLandmarks.remove(playerId)
        }
        this.imageWidth = width
        this.imageHeight = height
        this.isFrontCamera = isFront
        postInvalidate()
    }

    private fun getPos(playerId: Int, type: Int): PointF? {
        return drawnLandmarks[playerId]?.get(type)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth == 0 || imageHeight == 0) return

        if (winner != null && !winSoundPlayed) {
            if (currentGameMode != GameMode.BALAP_GEOL) {
                (context as? BasePoseActivity)?.playVictorySound()
            }
            winSoundPlayed = true
        }
        
        // 60 FPS Interpolation: Menghaluskan patahan frame kamera
        var needsAnimation = false
        val interpSpeed = 0.25f // Tingkat kelengketan (0.25 membuat transisi sangat mulus mengisi gap antar frame)
        
        for ((playerId, targets) in targetLandmarks) {
            needsAnimation = true // Selalu update 60 FPS jika ada player agar tracking tidak patah-patah
            val drawn = drawnLandmarks.getOrPut(playerId) { mutableMapOf() }
            for ((type, targetPos) in targets) {
                val drawnPos = drawn[type]
                if (drawnPos == null) {
                    drawn[type] = PointF(targetPos.x, targetPos.y) // Spawning awal langsung di tempat
                } else {
                    drawnPos.x += (targetPos.x - drawnPos.x) * interpSpeed
                    drawnPos.y += (targetPos.y - drawnPos.y) * interpSpeed
                }
            }
        }

        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val canvasOffsetX = (width - imageWidth * scale) / 2f
        val canvasOffsetY = (height - imageHeight * scale) / 2f

        // --- LAYER 1: Background Masking ---
        // Background has been removed as requested

        // Apply Screen Shake for everything above background
        if (shakeIntensity > 0) {
            val shakeX = (random.nextFloat() - 0.5f) * shakeIntensity
            val shakeY = (random.nextFloat() - 0.5f) * shakeIntensity
            canvas.translate(shakeX, shakeY)
            shakeIntensity *= 0.8f
            if (shakeIntensity < 0.5f) shakeIntensity = 0f
        }
        
        // --- LAYER 2: Center Line (Always on Top of background) ---
        val dashedPaint = Paint().apply {
            color = Color.parseColor("#80FFFFFF") // Semi-transparent white
            style = Paint.Style.STROKE
            strokeWidth = 25f // Even thicker
            pathEffect = DashPathEffect(floatArrayOf(50f, 50f), 0f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), dashedPaint)

        // Draw Game Content
        when (currentGameMode) {
            GameMode.KESATRIA -> drawKesatriaPCD(canvas)
            GameMode.BALAP_GEOL -> drawBalapGeol(canvas)
            GameMode.TIRU_GAYA -> drawTiruGaya(canvas)
            GameMode.NONE -> {}
        }

        drawHUD(canvas)
        
        val iterator = particles.iterator()
        val particlePaint = Paint().apply { style = Paint.Style.FILL }
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.5f
            p.alpha = (p.alpha * 0.92f).toInt()
            p.life--
            if (p.life <= 0 || p.alpha <= 10) {
                iterator.remove()
            } else {
                particlePaint.color = p.color
                particlePaint.alpha = p.alpha
                canvas.drawCircle(p.x, p.y, p.size, particlePaint)
            }
        }

        val textIterator = floatingTexts.iterator()
        val floatTextPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 60f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            typeface = gameTypeface
        }
        while (textIterator.hasNext()) {
            val ft = textIterator.next()
            ft.y -= 3f // Float up
            ft.alpha *= 0.9f
            ft.life--
            if (ft.life <= 0 || ft.alpha <= 10f) {
                textIterator.remove()
            } else {
                floatTextPaint.alpha = ft.alpha.toInt()
                canvas.drawText(ft.text, ft.x, ft.y, floatTextPaint)
            }
        }
        
        if (currentGameMode == GameMode.KESATRIA && rocks.any { !it.isDestroyed && it.rect.bottom < height * 0.85f }) postInvalidateOnAnimation()
        if (currentGameMode == GameMode.KESATRIA && projectiles.isNotEmpty()) postInvalidateOnAnimation()
        if (particles.isNotEmpty() || floatingTexts.isNotEmpty() || shakeIntensity > 0 || needsAnimation || winner != null) postInvalidateOnAnimation()
    }

    private fun drawTiruGaya(canvas: Canvas) {
        // As requested, no skeleton is visualized in Tiru Gaya
    }

    private fun drawKesatriaPCD(canvas: Canvas) {
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val canvasOffsetX = (width - imageWidth * scale) / 2f
        val canvasOffsetY = (height - imageHeight * scale) / 2f

        val hpPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 35f
            isFakeBoldText = true
            typeface = gameTypeface
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            textAlign = Paint.Align.CENTER
        }

        rocks.forEach { rock ->
            if (!rock.isDestroyed) {
                val currentHP = 5 - rock.hits
                canvas.drawText("HP: $currentHP", rock.rect.centerX(), rock.rect.top - 10f, hpPaint)

                if (rock.shakeAmount > 0) {
                    val sx = (Math.random().toFloat() - 0.5f) * rock.shakeAmount
                    val offsetRect = RectF(rock.rect)
                    offsetRect.offset(sx, 0f)
                    canvas.drawBitmap(boxBitmap, null, offsetRect, null)
                    rock.shakeAmount -= 2f
                } else {
                    canvas.drawBitmap(boxBitmap, null, rock.rect, null)
                }
            }
        }

        playersPose.forEach { (id, data) ->
            val (_, pXOffset) = data
            val tx = { x: Float -> 
                var sx = (x + pXOffset) * scale + canvasOffsetX
                if (isFrontCamera) sx = width - sx
                sx
            }
            val ty = { y: Float -> y * scale + canvasOffsetY }
            val ls = getPos(id, PoseLandmark.LEFT_SHOULDER)
            val rs = getPos(id, PoseLandmark.RIGHT_SHOULDER)
            if (ls != null && rs != null) {
                val sw = Math.hypot((tx(ls.x) - tx(rs.x)).toDouble(), (ty(ls.y) - ty(rs.y)).toDouble()).toFloat()
                drawBody(canvas, id, tx, ty, sw * 1.55f)
                drawShoulders(canvas, id, tx, ty, sw)
                drawHands(canvas, id, tx, ty, sw * 0.48f)
                drawHead(canvas, id, tx, ty, sw * 0.95f)
            }
        }
    }

    private fun drawBalapGeol(canvas: Canvas) {
        if (p1Drawable == null || p2Drawable == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val src1 = ImageDecoder.createSource(context.resources, R.drawable.karakter_kicau_mania)
                    val d1 = ImageDecoder.decodeDrawable(src1)
                    if (d1 is AnimatedImageDrawable) d1.start()
                    p1Drawable = d1
                    
                    val src2 = ImageDecoder.createSource(context.resources, R.drawable.karakter_kicau_mania)
                    val d2 = ImageDecoder.decodeDrawable(src2)
                    if (d2 is AnimatedImageDrawable) d2.start()
                    p2Drawable = d2
                } catch (e: Exception) {
                    p1Drawable = context.getDrawable(R.drawable.box)
                    p2Drawable = context.getDrawable(R.drawable.box)
                }
            } else {
                p1Drawable = context.getDrawable(R.drawable.karakter_kicau_mania)
                p2Drawable = context.getDrawable(R.drawable.karakter_kicau_mania)
            }
        }
        
        // Disco Filter Effect
        discoHue = (discoHue + 3f) % 360f
        discoPaint.color = Color.HSVToColor(70, floatArrayOf(discoHue, 1f, 1f))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), discoPaint)
        
        // Track Finish Line
        val finishLineY = 200f
        val startY = height - 150f
        val trackLength = startY - finishLineY
        
        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 10f
            style = Paint.Style.STROKE
        }
        
        // Garis Start dan Finish masing-masing player
        val p1CenterX = width * 0.25f
        val p2CenterX = width * 0.75f
        
        // P1 Finish & Start
        canvas.drawLine(p1CenterX - 200f, finishLineY, p1CenterX + 200f, finishLineY, paint)
        canvas.drawLine(p1CenterX - 200f, startY + 50f, p1CenterX + 200f, startY + 50f, paint)
        
        // P2 Finish & Start
        canvas.drawLine(p2CenterX - 200f, finishLineY, p2CenterX + 200f, finishLineY, paint)
        canvas.drawLine(p2CenterX - 200f, startY + 50f, p2CenterX + 200f, startY + 50f, paint)
        
        paint.textSize = 60f
        paint.style = Paint.Style.FILL
        paint.typeface = gameTypeface
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("FINISH", p1CenterX, finishLineY - 20f, paint)
        canvas.drawText("FINISH", p2CenterX, finishLineY - 20f, paint)
        
        p1Drawable?.let {
            val p1X = width * 0.25f
            val p1Y = startY - (balapP1Progress * trackLength)
            it.setBounds((p1X - 250f).toInt(), (p1Y - 250f).toInt(), (p1X + 250f).toInt(), (p1Y + 250f).toInt())
            it.draw(canvas)
        }
        
        p2Drawable?.let {
            val p2X = width * 0.75f
            val p2Y = startY - (balapP2Progress * trackLength)
            it.setBounds((p2X - 250f).toInt(), (p2Y - 250f).toInt(), (p2X + 250f).toInt(), (p2Y + 250f).toInt())
            it.draw(canvas)
        }
        
        if (winner == null) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawDanceOverlay(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 60f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            typeface = gameTypeface
        }
        canvas.drawText("POSE: $currentTargetPoseName", width / 2f, 150f, paint)

        val barWidth = width * 0.35f
        val barHeight = 25f
        val p1Paint = Paint().apply { color = Color.CYAN }
        canvas.drawRect(width * 0.1f, 180f, width * 0.1f + barWidth * p1MatchProgress, 180f + barHeight, p1Paint)
        
        val p2Paint = Paint().apply { color = Color.MAGENTA }
        canvas.drawRect(width * 0.9f - barWidth * p2MatchProgress, 180f, width * 0.9f, 180f + barHeight, p2Paint)
    }

    private fun drawFighterOverlay(canvas: Canvas) {
        val p = Paint().apply { isAntiAlias = true }
        projectiles.forEach { proj ->
            p.color = if (proj.ownerId == 1) Color.CYAN else Color.MAGENTA
            canvas.drawCircle(proj.x, proj.y, 15f, p)
            spawnHitParticles(proj.x, proj.y, p.color) // Trail effect
        }
    }

    private fun drawHUD(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.parseColor("#FFEB3B") // Yellow from image
            textSize = 65f
            isFakeBoldText = true
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
            typeface = gameTypeface
        }
        
        if (currentGameMode != GameMode.BALAP_GEOL) {
            // Player 1 Score (Top Left)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("P1: $scoreP1", 50f, 100f, paint)
            
            // Player 2 Score (Top Right)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("P2: $scoreP2", width - 50f, 100f, paint)
        }



        winner?.let { winName ->
            drawWinnerOverlay(canvas, winName)
        }
    }

    private fun drawWinnerOverlay(canvas: Canvas, winName: String) {
        val overlayPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 100f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(15f, 0f, 0f, Color.RED)
            typeface = gameTypeface
        }

        val bounce = (Math.sin(System.currentTimeMillis() * 0.01) * 20).toFloat()
        canvas.drawText("WINNER!", width / 2f, height / 2f - 50f + bounce, textPaint)
        
        textPaint.textSize = 80f
        textPaint.color = Color.WHITE
        canvas.drawText(winName, width / 2f, height / 2f + 80f + bounce, textPaint)
        
        // Add some "celebration" lines
        val linePaint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 10f
            style = Paint.Style.STROKE
        }
        val time = System.currentTimeMillis() * 0.005
        for (i in 0 until 8) {
            val angle = i * (Math.PI / 4) + time
            val r1 = 200f
            val r2 = 300f
            canvas.drawLine(
                width / 2f + Math.cos(angle).toFloat() * r1,
                height / 2f + Math.sin(angle).toFloat() * r1,
                width / 2f + Math.cos(angle).toFloat() * r2,
                height / 2f + Math.sin(angle).toFloat() * r2,
                linePaint
            )
        }
    }

    private fun drawRealisticMask(canvas: Canvas, mask: SegmentationMask, scale: Float, offsetX: Float, offsetY: Float, paint: Paint, playerId: Int) {
        val maskBuffer = mask.buffer
        val maskWidth = mask.width
        val maskHeight = mask.height
        val pixelCount = maskWidth * maskHeight
        
        var pixels = playerMaskPixels[playerId]
        if (pixels == null || pixels.size != pixelCount) {
            pixels = IntArray(pixelCount)
            playerMaskPixels[playerId] = pixels
            playerMaskBitmaps[playerId]?.recycle()
            playerMaskBitmaps[playerId] = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        }
        
        maskBuffer.rewind()
        // Super fast bulk pixel writing
        for (i in 0 until pixelCount) {
            val confidence = maskBuffer.float
            pixels[i] = if (confidence > 0.5f) Color.BLACK else Color.TRANSPARENT
        }
        
        val bitmap = playerMaskBitmaps[playerId]!!
        bitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        
        // Handle Mirroring for Front Camera
        val matrix = Matrix()
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(maskWidth.toFloat(), 0f)
        }
        matrix.postScale(scale, scale)
        
        // Calculate screen position
        val screenX = if (isFrontCamera) {
            width - (offsetX + maskWidth) * scale
        } else {
            offsetX * scale
        }
        matrix.postTranslate(screenX, offsetY * scale)
        
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawHumanSilhouette(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, paint: Paint) {
        val ls = getPos(id, PoseLandmark.LEFT_SHOULDER)
        val rs = getPos(id, PoseLandmark.RIGHT_SHOULDER)
        val lh = getPos(id, PoseLandmark.LEFT_HIP)
        val rh = getPos(id, PoseLandmark.RIGHT_HIP)
        
        if (ls == null || rs == null) return

        val shoulderWidth = Math.hypot(
            (tx(ls.x) - tx(rs.x)).toDouble(),
            (ty(ls.y) - ty(rs.y)).toDouble()
        ).toFloat()
        
        val silhouettePaint = Paint(paint).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.FILL_AND_STROKE
        }

        // --- TORSO ---
        val torsoPath = Path()
        torsoPath.moveTo(tx(ls.x), ty(ls.y))
        torsoPath.lineTo(tx(rs.x), ty(rs.y))
        if (rh != null) torsoPath.lineTo(tx(rh.x), ty(rh.y))
        if (lh != null) torsoPath.lineTo(tx(lh.x), ty(lh.y))
        torsoPath.close()
        silhouettePaint.strokeWidth = shoulderWidth * 0.2f
        canvas.drawPath(torsoPath, silhouettePaint)

        // --- HEAD ---
        val nose = getPos(id, PoseLandmark.NOSE)
        if (nose != null) {
            val headRadius = shoulderWidth * 0.5f
            canvas.drawCircle(tx(nose.x), ty(nose.y), headRadius, silhouettePaint)
            
            // Neck
            val midShoulderX = (tx(ls.x) + tx(rs.x)) / 2f
            val midShoulderY = (ty(ls.y) + ty(rs.y)) / 2f
            silhouettePaint.strokeWidth = shoulderWidth * 0.4f
            canvas.drawLine(tx(nose.x), ty(nose.y), midShoulderX, midShoulderY, silhouettePaint)
        }

        // --- LIMBS (Arms and Legs) ---
        // We use paths or very thick lines to simulate body volume
        val connections = listOf(
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )

        for (conn in connections) {
            val start = getPos(id, conn.first)
            val end = getPos(id, conn.second)
            if (start != null && end != null) {
                // Thicker for upper limbs, slightly thinner for lower
                val isUpper = conn.first == PoseLandmark.LEFT_SHOULDER || conn.first == PoseLandmark.RIGHT_SHOULDER ||
                              conn.first == PoseLandmark.LEFT_HIP || conn.first == PoseLandmark.RIGHT_HIP
                
                silhouettePaint.strokeWidth = if (isUpper) shoulderWidth * 0.65f else shoulderWidth * 0.5f
                canvas.drawLine(tx(start.x), ty(start.y), tx(end.x), ty(end.y), silhouettePaint)
            }
        }
    }

    private fun drawBody(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, bodyWidth: Float) {
        val leftShoulder = getPos(id, PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = getPos(id, PoseLandmark.RIGHT_SHOULDER)
        val leftHip = getPos(id, PoseLandmark.LEFT_HIP)
        val rightHip = getPos(id, PoseLandmark.RIGHT_HIP)
        if (leftShoulder != null && rightShoulder != null) {
            val bodyBitmap = if (id == 1) body1Bitmap else body2Bitmap
            val lsX = tx(leftShoulder.x)
            val lsY = ty(leftShoulder.y)
            val rsX = tx(rightShoulder.x)
            val rsY = ty(rightShoulder.y)
            val midShoulderX = (lsX + rsX) / 2f
            val midShoulderY = (lsY + rsY) / 2f
            val bodyHeight = if (leftHip != null && rightHip != null) {
                val midHipY = (ty(leftHip.y) + ty(rightHip.y)) / 2f
                (midHipY - midShoulderY) * 1.3f
            } else {
                bodyWidth * 1.1f
            }
            val scaledWidth = bodyWidth * Config.bodyScale
            val scaledHeight = bodyHeight * Config.bodyScale

            canvas.withSave {
                val angle = if (isFrontCamera) {
                    Math.toDegrees(Math.atan2((rsY - lsY).toDouble(), (rsX - lsX).toDouble())).toFloat()
                } else {
                    Math.toDegrees(Math.atan2((lsY - rsY).toDouble(), (lsX - rsX).toDouble())).toFloat()
                }
                canvas.rotate(angle, midShoulderX, midShoulderY)
                if (isFrontCamera) canvas.scale(-1f, 1f, midShoulderX, midShoulderY)
                val rect = RectF(
                    midShoulderX - scaledWidth / 2 + Config.bodyOffsetX, 
                    midShoulderY - scaledHeight * 0.25f + Config.bodyOffsetY, 
                    midShoulderX + scaledWidth / 2 + Config.bodyOffsetX, 
                    midShoulderY + scaledHeight * 0.75f + Config.bodyOffsetY
                )
                canvas.drawBitmap(bodyBitmap, null, rect, null)
            }
        }
    }

    private fun drawShoulders(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, shoulderWidth: Float) {
        val leftShoulder = getPos(id, PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = getPos(id, PoseLandmark.RIGHT_SHOULDER)
        
        if (leftShoulder == null || rightShoulder == null) return
        
        // Tukar posisi gambar karena kamera depan itu *mirrored*
        val leftBitmap = if (id == 1) shoulder1RightBitmap else shoulder2RightBitmap
        val rightBitmap = if (id == 1) shoulder1LeftBitmap else shoulder2LeftBitmap
        
        val padSize = shoulderWidth * 0.7f // Size relative to player width
        
        val lsX = tx(leftShoulder.x)
        val lsY = ty(leftShoulder.y)
        val rsX = tx(rightShoulder.x)
        val rsY = ty(rightShoulder.y)
        
        val angle = if (isFrontCamera) {
            Math.toDegrees(Math.atan2((rsY - lsY).toDouble(), (rsX - lsX).toDouble())).toFloat()
        } else {
            Math.toDegrees(Math.atan2((lsY - rsY).toDouble(), (lsX - rsX).toDouble())).toFloat()
        }
        
        val scaledPadSize = padSize * Config.shoulderScale
        
        // Draw Left Shoulder Pad (left side of screen -> physical right shoulder)
        canvas.withSave {
            canvas.rotate(angle, rsX, rsY)
            val rect = RectF(
                rsX - scaledPadSize / 2 + Config.leftShoulderOffsetX, 
                rsY - scaledPadSize / 2 + Config.leftShoulderOffsetY, 
                rsX + scaledPadSize / 2 + Config.leftShoulderOffsetX, 
                rsY + scaledPadSize / 2 + Config.leftShoulderOffsetY
            )
            canvas.drawBitmap(leftBitmap, null, rect, null)
        }
        
        // Draw Right Shoulder Pad (right side of screen -> physical left shoulder)
        canvas.withSave {
            canvas.rotate(angle, lsX, lsY)
            val rect = RectF(
                lsX - scaledPadSize / 2 + Config.rightShoulderOffsetX, 
                lsY - scaledPadSize / 2 + Config.rightShoulderOffsetY, 
                lsX + scaledPadSize / 2 + Config.rightShoulderOffsetX, 
                lsY + scaledPadSize / 2 + Config.rightShoulderOffsetY
            )
            canvas.drawBitmap(rightBitmap, null, rect, null)
        }
    }

    private fun drawHands(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, handSize: Float) {
        val leftWrist = getPos(id, PoseLandmark.LEFT_WRIST)
        val rightWrist = getPos(id, PoseLandmark.RIGHT_WRIST)
        val leftElbow = getPos(id, PoseLandmark.LEFT_ELBOW)
        val rightElbow = getPos(id, PoseLandmark.RIGHT_ELBOW)
        leftWrist?.let { wrist -> drawRotatedHand(canvas, handRightBitmap, wrist, leftElbow, tx, ty, handSize, Config.rightHandOffsetX, Config.rightHandOffsetY) }
        rightWrist?.let { wrist -> drawRotatedHand(canvas, handLeftBitmap, wrist, rightElbow, tx, ty, handSize, Config.leftHandOffsetX, Config.leftHandOffsetY) }
    }

    private fun drawHead(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, headSize: Float) {
        val nose = getPos(id, PoseLandmark.NOSE)
        val leftEye = getPos(id, PoseLandmark.LEFT_EYE)
        val rightEye = getPos(id, PoseLandmark.RIGHT_EYE)
        nose?.let { n ->
            val headBitmap = if (id == 1) head1Bitmap else head2Bitmap
            val hx = tx(n.x)
            val hy = ty(n.y)
            canvas.withSave {
                if (leftEye != null && rightEye != null) {
                    val angle = if (isFrontCamera) {
                        Math.toDegrees(Math.atan2((ty(rightEye.y) - ty(leftEye.y)).toDouble(), (tx(rightEye.x) - tx(leftEye.x)).toDouble())).toFloat()
                    } else {
                        Math.toDegrees(Math.atan2((ty(leftEye.y) - ty(rightEye.y)).toDouble(), (tx(leftEye.x) - tx(rightEye.x)).toDouble())).toFloat()
                    }
                    canvas.rotate(angle, hx, hy)
                }
                val scaledHeadSize = headSize * Config.headScale
                if (isFrontCamera) canvas.scale(-1f, 1f, hx, hy)
                val rect = RectF(
                    hx - scaledHeadSize / 2 + Config.headOffsetX, 
                    hy - scaledHeadSize * 0.75f + Config.headOffsetY, 
                    hx + scaledHeadSize / 2 + Config.headOffsetX, 
                    hy + scaledHeadSize * 0.25f + Config.headOffsetY
                )
                canvas.drawBitmap(headBitmap, null, rect, null)
            }
        }
    }

    private fun drawRotatedHand(canvas: Canvas, bitmap: Bitmap, wrist: PointF, elbow: PointF?, tx: (Float) -> Float, ty: (Float) -> Float, handSize: Float, offsetX: Float, offsetY: Float) {
        val wx = tx(wrist.x)
        val wy = ty(wrist.y)
        
        val scaledHandSize = handSize * Config.handScale
        
        canvas.withSave {
            if (elbow != null) {
                val ex = tx(elbow.x)
                val ey = ty(elbow.y)
                
                // Rotasi tangan mengikuti orientasi lengan bawah
                val angle = if (isFrontCamera) {
                    Math.toDegrees(Math.atan2((wy - ey).toDouble(), (wx - ex).toDouble())).toFloat()
                } else {
                    Math.toDegrees(Math.atan2((ey - wy).toDouble(), (ex - wx).toDouble())).toFloat()
                }
                // Rotasi tangan biasanya miring karena gambar default menghadap atas, kita kompensasi
                canvas.rotate(angle - 90f, wx, wy)
            }
            
            if (isFrontCamera) canvas.scale(-1f, 1f, wx, wy)
            
            val rect = RectF(
                wx - scaledHandSize / 2 + offsetX, 
                wy - scaledHandSize / 2 + offsetY, 
                wx + scaledHandSize / 2 + offsetX, 
                wy + scaledHandSize / 2 + offsetY
            )
            canvas.drawBitmap(bitmap, null, rect, null)
        }
    }

    private fun drawRawSkeleton(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float) {
        val data = playersPose[id] ?: return
        val pose = data.first
        
        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val connections = listOf(
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )

        for (edge in connections) {
            val start = pose.getPoseLandmark(edge.first)
            val end = pose.getPoseLandmark(edge.second)
            if (start != null && end != null && start.inFrameLikelihood > 0.2f && end.inFrameLikelihood > 0.2f) {
                canvas.drawLine(tx(start.position.x), ty(start.position.y), tx(end.position.x), ty(end.position.y), paint)
            }
        }
        
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
        if (nose != null && leftEar != null && rightEar != null) {
            val radius = java.lang.Math.hypot((tx(leftEar.position.x) - tx(rightEar.position.x)).toDouble(), 
                                  (ty(leftEar.position.y) - ty(rightEar.position.y)).toDouble()).toFloat() / 1.5f
            val headPaint = Paint(paint).apply { style = Paint.Style.FILL; color = Color.parseColor("#80FFFFFF") }
            canvas.drawCircle(tx(nose.position.x), ty(nose.position.y), java.lang.Math.max(radius, 20f), headPaint)
        }
    }
}
