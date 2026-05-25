package org.example.kinetiqfun

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
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

    internal val playersPose = mutableMapOf<Int, Pair<Pose, Float>>()
    internal val gameTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(context, R.font.game_font)
    }

    private val targetLandmarks = mutableMapOf<Int, MutableMap<Int, PointF>>()
    private val drawnLandmarks = mutableMapOf<Int, MutableMap<Int, PointF>>()

    internal var imageWidth: Int = 0
    internal var imageHeight: Int = 0
    internal var isFrontCamera: Boolean = true

    internal val handLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_left)
    internal val handRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_right)
    internal val head1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head1)
    internal val head2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head2)
    internal val body1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body1)
    internal val body2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body2)
    
    internal val shoulder1LeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder1_left)
    internal val shoulder1RightBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder1_right)
    internal val shoulder2LeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder2_left)
    internal val shoulder2RightBitmap = BitmapFactory.decodeResource(resources, R.drawable.shoulder2_right)

    internal val boxBitmap = BitmapFactory.decodeResource(resources, R.drawable.box)

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

    internal var balapP1Progress = 0f
    internal var balapP2Progress = 0f
    
    internal var p1Drawable: Drawable? = null
    internal var p2Drawable: Drawable? = null

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val source1 = android.graphics.ImageDecoder.createSource(resources, R.drawable.karakter_kicau_mania)
                p1Drawable = android.graphics.ImageDecoder.decodeDrawable(source1)
                (p1Drawable as? android.graphics.drawable.AnimatedImageDrawable)?.let {
                    it.callback = this
                    it.start()
                }
                
                val source2 = android.graphics.ImageDecoder.createSource(resources, R.drawable.karakter_kicau_mania)
                p2Drawable = android.graphics.ImageDecoder.decodeDrawable(source2)
                (p2Drawable as? android.graphics.drawable.AnimatedImageDrawable)?.let {
                    it.callback = this
                    it.start()
                }
            } catch (e: Exception) {}
        } else {
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.karakter_kicau_mania)
            p1Drawable = android.graphics.drawable.BitmapDrawable(resources, bmp)
            p2Drawable = android.graphics.drawable.BitmapDrawable(resources, bmp)
        }
    }
    

    // Disco Filter
    internal var discoHue = 0f
    internal val discoPaint = Paint().apply { style = Paint.Style.FILL }
    
    // --- REUSED DRAWING OBJECTS ---
    internal val commonPaint = Paint().apply { isAntiAlias = true }
    internal val hudPaint = Paint().apply { isFakeBoldText = true; setShadowLayer(10f, 0f, 0f, Color.BLACK) }
    internal val dashedPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 25f
        pathEffect = DashPathEffect(floatArrayOf(50f, 50f), 0f)
        strokeCap = Paint.Cap.ROUND
    }
    internal val particlePaint = Paint().apply { style = Paint.Style.FILL }
    internal val floatTextPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 60f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    internal val hpPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 35f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }
    internal val trackPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }
    internal val winnerOverlayPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    internal val winnerTextPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 100f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(15f, 0f, 0f, Color.RED)
    }
    internal val celebrationLinePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    internal val reusablePath = Path()
    internal val tempRect = RectF()
    internal val tempMatrix = Matrix()

    private val kesatriaRenderer = KesatriaRenderer(this)
    private val balapGeolRenderer = BalapGeolRenderer(this)

    internal var rocks = mutableListOf<Rock>()
    private var nameP1 = ""
    private var nameP2 = ""
    private var scoreP1 = 0
    private var scoreP2 = 0
    private var winner: String? = null
    private var winSoundPlayed = false

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var size: Float, var alpha: Int,
        val color: Int, var life: Int,
        val type: ParticleType = ParticleType.CIRCLE
    )

    enum class ParticleType { CIRCLE, SQUARE, STAR, SPARK }
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
        for (i in 0..30) {
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = (random.nextFloat() - 0.5f) * 40f,
                vy = (random.nextFloat() - 0.5f) * 40f - 15f,
                size = random.nextFloat() * 30f + 15f,
                alpha = 255,
                color = if (random.nextBoolean()) Color.DKGRAY else Color.GRAY,
                life = 50 + random.nextInt(30),
                type = if (random.nextBoolean()) ParticleType.SQUARE else ParticleType.CIRCLE
            ))
        }
    }

    private fun spawnHitSparks(rect: RectF) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        for (i in 0..12) {
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = (random.nextFloat() - 0.5f) * 25f,
                vy = (random.nextFloat() - 0.5f) * 25f - 10f,
                size = random.nextFloat() * 12f + 6f,
                alpha = 255,
                color = if (random.nextBoolean()) Color.YELLOW else Color.rgb(255, 165, 0),
                life = 20 + random.nextInt(15),
                type = ParticleType.SPARK
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




    private fun spawnVictoryParticles() {
        for (i in 0 until 120) {
            // Left Cannon
            val vx = (random.nextFloat() - 0.1f) * 50f
            val vy = -(25f + random.nextFloat() * 50f)
            val color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            particles.add(Particle(
                width * 0.1f, height.toFloat(), vx, vy, 
                15f + random.nextFloat() * 25f, 255, color, 100 + random.nextInt(50),
                type = if (random.nextBoolean()) ParticleType.STAR else ParticleType.CIRCLE
            ))
            
            // Right Cannon
            val vx2 = (random.nextFloat() - 0.9f) * 50f
            val color2 = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            particles.add(Particle(
                width * 0.9f, height.toFloat(), vx2, vy, 
                15f + random.nextFloat() * 25f, 255, color2, 100 + random.nextInt(50),
                type = if (random.nextBoolean()) ParticleType.STAR else ParticleType.CIRCLE
            ))
        }
    }

    fun setResults(playerId: Int, pose: Pose?, mask: Nothing? = null, width: Int, height: Int, isFront: Boolean, xOffset: Float = 0f) {
        if (pose != null && pose.allPoseLandmarks.size > 15) {
            playersPose[playerId] = Pair(pose, xOffset)
            
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

    internal fun getPos(playerId: Int, type: Int): PointF? {
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
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), dashedPaint)

        // Draw Game Content
        when (currentGameMode) {
            GameMode.KESATRIA -> kesatriaRenderer.onDraw(canvas)
            GameMode.BALAP_GEOL -> balapGeolRenderer.onDraw(canvas)
            GameMode.TIRU_GAYA -> drawTiruGaya(canvas)
            GameMode.NONE -> {}
        }

        drawHUD(canvas)
        
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.7f // Increased gravity for snappier feel
            p.alpha = (p.alpha * 0.94f).toInt()
            p.life--
            if (p.life <= 0 || p.alpha <= 10) {
                iterator.remove()
            } else {
                particlePaint.color = p.color
                particlePaint.alpha = p.alpha
                
                when (p.type) {
                    ParticleType.CIRCLE -> canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                    ParticleType.SQUARE -> canvas.drawRect(p.x - p.size, p.y - p.size, p.x + p.size, p.y + p.size, particlePaint)
                    ParticleType.STAR -> {
                        // Simple 4-point star/diamond
                        reusablePath.reset()
                        reusablePath.moveTo(p.x, p.y - p.size * 1.5f)
                        reusablePath.lineTo(p.x + p.size, p.y)
                        reusablePath.lineTo(p.x, p.y + p.size * 1.5f)
                        reusablePath.lineTo(p.x - p.size, p.y)
                        reusablePath.close()
                        canvas.drawPath(reusablePath, particlePaint)
                    }
                    ParticleType.SPARK -> {
                        // Line spark
                        particlePaint.strokeWidth = p.size / 3f
                        canvas.drawLine(p.x, p.y, p.x - p.vx * 1.5f, p.y - p.vy * 1.5f, particlePaint)
                    }
                }
            }
        }

        val textIterator = floatingTexts.iterator()
        floatTextPaint.typeface = gameTypeface
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
        if (particles.isNotEmpty() || floatingTexts.isNotEmpty() || shakeIntensity > 0 || needsAnimation || winner != null) postInvalidateOnAnimation()
    }

    private fun drawTiruGaya(canvas: Canvas) {
        // As requested, no skeleton is visualized in Tiru Gaya
    }

    private fun drawHUD(canvas: Canvas) {
        hudPaint.color = Color.parseColor("#FFEB3B") // Yellow from image
        hudPaint.textSize = 65f
        hudPaint.typeface = gameTypeface
        
        if (currentGameMode != GameMode.BALAP_GEOL) {
            // Player 1 Score (Top Left)
            hudPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("P1: $scoreP1", 50f, 100f, hudPaint)
            
            // Player 2 Score (Top Right)
            hudPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("P2: $scoreP2", width - 50f, 100f, hudPaint)
        }

        winner?.let { winName ->
            drawWinnerOverlay(canvas, winName)
        }
    }

    private fun drawWinnerOverlay(canvas: Canvas, winName: String) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), winnerOverlayPaint)

        winnerTextPaint.typeface = gameTypeface

        val bounce = (Math.sin(System.currentTimeMillis() * 0.01) * 20).toFloat()
        winnerTextPaint.textSize = 100f
        winnerTextPaint.color = Color.YELLOW
        canvas.drawText("WINNER!", width / 2f, height / 2f - 50f + bounce, winnerTextPaint)
        
        winnerTextPaint.textSize = 80f
        winnerTextPaint.color = Color.WHITE
        canvas.drawText(winName, width / 2f, height / 2f + 80f + bounce, winnerTextPaint)
        
        // Add some "celebration" lines
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
                celebrationLinePaint
            )
        }
    }

    internal fun drawBody(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, bodyWidth: Float) {
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
                tempRect.set(
                    midShoulderX - scaledWidth / 2 + Config.bodyOffsetX, 
                    midShoulderY - scaledHeight * 0.25f + Config.bodyOffsetY, 
                    midShoulderX + scaledWidth / 2 + Config.bodyOffsetX, 
                    midShoulderY + scaledHeight * 0.75f + Config.bodyOffsetY
                )
                canvas.drawBitmap(bodyBitmap, null, tempRect, null)
            }
        }
    }

    internal fun drawShoulders(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, shoulderWidth: Float) {
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
            tempRect.set(
                rsX - scaledPadSize / 2 + Config.leftShoulderOffsetX, 
                rsY - scaledPadSize / 2 + Config.leftShoulderOffsetY, 
                rsX + scaledPadSize / 2 + Config.leftShoulderOffsetX, 
                rsY + scaledPadSize / 2 + Config.leftShoulderOffsetY
            )
            canvas.drawBitmap(leftBitmap, null, tempRect, null)
        }
        
        // Draw Right Shoulder Pad (right side of screen -> physical left shoulder)
        canvas.withSave {
            canvas.rotate(angle, lsX, lsY)
            tempRect.set(
                lsX - scaledPadSize / 2 + Config.rightShoulderOffsetX, 
                lsY - scaledPadSize / 2 + Config.rightShoulderOffsetY, 
                lsX + scaledPadSize / 2 + Config.rightShoulderOffsetX, 
                lsY + scaledPadSize / 2 + Config.rightShoulderOffsetY
            )
            canvas.drawBitmap(rightBitmap, null, tempRect, null)
        }
    }

    internal fun drawHands(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, handSize: Float) {
        val leftWrist = getPos(id, PoseLandmark.LEFT_WRIST)
        val rightWrist = getPos(id, PoseLandmark.RIGHT_WRIST)
        val leftElbow = getPos(id, PoseLandmark.LEFT_ELBOW)
        val rightElbow = getPos(id, PoseLandmark.RIGHT_ELBOW)
        leftWrist?.let { wrist -> drawRotatedHand(canvas, handRightBitmap, wrist, leftElbow, tx, ty, handSize, Config.rightHandOffsetX, Config.rightHandOffsetY) }
        rightWrist?.let { wrist -> drawRotatedHand(canvas, handLeftBitmap, wrist, rightElbow, tx, ty, handSize, Config.leftHandOffsetX, Config.leftHandOffsetY) }
    }

    internal fun drawHead(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, headSize: Float) {
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
                tempRect.set(
                    hx - scaledHeadSize / 2 + Config.headOffsetX, 
                    hy - scaledHeadSize * 0.75f + Config.headOffsetY, 
                    hx + scaledHeadSize / 2 + Config.headOffsetX, 
                    hy + scaledHeadSize * 0.25f + Config.headOffsetY
                )
                canvas.drawBitmap(headBitmap, null, tempRect, null)
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
            
            tempRect.set(
                wx - scaledHandSize / 2 + offsetX, 
                wy - scaledHandSize / 2 + offsetY, 
                wx + scaledHandSize / 2 + offsetX, 
                wy + scaledHandSize / 2 + offsetY
            )
            canvas.drawBitmap(bitmap, null, tempRect, null)
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || who === p1Drawable || who === p2Drawable
    }

    /** Recycle all loaded bitmaps to free memory. Call from Activity.onDestroy(). */
    fun recycleBitmaps() {
        handLeftBitmap.recycle()
        handRightBitmap.recycle()
        head1Bitmap.recycle()
        head2Bitmap.recycle()
        body1Bitmap.recycle()
        body2Bitmap.recycle()
        shoulder1LeftBitmap.recycle()
        shoulder1RightBitmap.recycle()
        shoulder2LeftBitmap.recycle()
        shoulder2RightBitmap.recycle()
        boxBitmap.recycle()
    }
}
