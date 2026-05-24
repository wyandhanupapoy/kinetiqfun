package org.example.kinetiqfun

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.*
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val playersPose = mutableMapOf<Int, Pair<Pose, Float>>()
    private val gameTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(context, R.font.game_font)
    }

    private val smoothedLandmarks = mutableMapOf<Int, MutableMap<Int, PointF>>()
    private val smoothingAlpha = 0.45f

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var isFrontCamera: Boolean = true

    private val handLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_left)
    private val handRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.hand_right)
    private val head1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head1)
    private val head2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.head2)
    private val body1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body1)
    private val body2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.body2)

    private val groundBitmap = BitmapFactory.decodeResource(resources, R.drawable.ground)
    private val rockBitmaps = listOf(
        BitmapFactory.decodeResource(resources, R.drawable.rock1),
        BitmapFactory.decodeResource(resources, R.drawable.rock2),
        BitmapFactory.decodeResource(resources, R.drawable.rock3)
    )

    data class Rock(
        val id: Int,
        var hits: Int = 0,
        val rect: RectF,
        var isDestroyed: Boolean = false,
        var velocityY: Float = 0f
    )

    enum class GameMode { KESATRIA, DANCE, FIGHTER }
    var currentGameMode = GameMode.KESATRIA

    private var rocks = mutableListOf<Rock>()
    private var nameP1 = ""
    private var nameP2 = ""
    private var scoreP1 = 0
    private var scoreP2 = 0
    private var winner: String? = null

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

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        isAntiAlias = true
    }

    fun updateKesatriaState(newRocks: List<Rock>, p1Name: String, p2Name: String, winName: String?) {
        if (currentGameMode != GameMode.KESATRIA) gameStartTime = System.currentTimeMillis()
        currentGameMode = GameMode.KESATRIA
        
        if (this.rocks.isEmpty()) {
            this.rocks = newRocks.toMutableList()
        } else {
            newRocks.forEachIndexed { index, newRock ->
                if (index < this.rocks.size) {
                    if (newRock.isDestroyed && !this.rocks[index].isDestroyed) {
                        spawnRockParticles(this.rocks[index].rect)
                    }
                    this.rocks[index].hits = newRock.hits
                    this.rocks[index].isDestroyed = newRock.isDestroyed
                }
            }
        }
        this.nameP1 = p1Name
        this.nameP2 = p2Name
        if (winName != null && this.winner == null) {
            spawnVictoryParticles()
        }
        this.winner = winName
        postInvalidateOnAnimation()
    }

    private fun spawnRockParticles(rect: RectF) {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        for (i in 0..15) {
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = (random.nextFloat() - 0.5f) * 20f,
                vy = (random.nextFloat() - 0.5f) * 20f - 5f,
                size = random.nextFloat() * 20f + 10f,
                alpha = 255,
                color = Color.GRAY,
                life = 30 + random.nextInt(20)
            ))
        }
    }

    fun updateDanceState(targetPose: String, p1Prog: Float, p2Prog: Float, s1: Int, s2: Int, p1Name: String, p2Name: String, winName: String?) {
        if (currentGameMode != GameMode.DANCE) gameStartTime = System.currentTimeMillis()
        currentGameMode = GameMode.DANCE
        currentTargetPoseName = targetPose
        p1MatchProgress = p1Prog
        p2MatchProgress = p2Prog
        scoreP1 = s1
        scoreP2 = s2
        this.nameP1 = p1Name
        this.nameP2 = p2Name
        if (winName != null && this.winner == null) {
            spawnVictoryParticles()
        }
        this.winner = winName
        postInvalidateOnAnimation()
    }

    fun updateFighterState(projs: List<Projectile>, h1: Int, h2: Int, p1Name: String, p2Name: String, winName: String?) {
        if (currentGameMode != GameMode.FIGHTER) gameStartTime = System.currentTimeMillis()
        currentGameMode = GameMode.FIGHTER
        
        if (h1 < this.hpP1) spawnHitParticles(width * 0.25f, height * 0.5f, Color.RED)
        if (h2 < this.hpP2) spawnHitParticles(width * 0.75f, height * 0.5f, Color.RED)

        projectiles.clear()
        projectiles.addAll(projs)
        hpP1 = h1
        hpP2 = h2
        this.nameP1 = p1Name
        this.nameP2 = p2Name
        if (winName != null && this.winner == null) {
            spawnVictoryParticles()
        }
        this.winner = winName
        postInvalidateOnAnimation()
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

    fun setResults(playerId: Int, pose: Pose?, width: Int, height: Int, isFront: Boolean, xOffset: Float = 0f) {
        if (pose != null && pose.allPoseLandmarks.size > 15) {
            playersPose[playerId] = Pair(pose, xOffset)
            updateSmoothedLandmarks(playerId, pose)
        } else {
            playersPose.remove(playerId)
            smoothedLandmarks.remove(playerId)
        }
        this.imageWidth = width
        this.imageHeight = height
        this.isFrontCamera = isFront
        postInvalidate()
    }

    private fun updateSmoothedLandmarks(playerId: Int, pose: Pose) {
        val playerSmoothed = smoothedLandmarks.getOrPut(playerId) { mutableMapOf() }
        pose.allPoseLandmarks.forEach { landmark ->
            val type = landmark.landmarkType
            val currentPos = landmark.position
            val prevPos = playerSmoothed[type]
            
            if (prevPos == null) {
                playerSmoothed[type] = PointF(currentPos.x, currentPos.y)
            } else {
                prevPos.x = smoothingAlpha * currentPos.x + (1f - smoothingAlpha) * prevPos.x
                prevPos.y = smoothingAlpha * currentPos.y + (1f - smoothingAlpha) * prevPos.y
            }
        }
    }

    private fun getPos(playerId: Int, type: Int): PointF? {
        return smoothedLandmarks[playerId]?.get(type)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth == 0 || imageHeight == 0) return

        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val canvasOffsetX = (width - imageWidth * scale) / 2f
        val canvasOffsetY = (height - imageHeight * scale) / 2f

        if (currentGameMode == GameMode.KESATRIA) {
            val groundCollisionY = height * 0.85f
            rocks.forEach { rock ->
                if (!rock.isDestroyed) {
                    if (rock.rect.bottom < groundCollisionY) {
                        rock.velocityY += 2.5f
                        rock.rect.offset(0f, rock.velocityY)
                    }
                    if (rock.rect.bottom >= groundCollisionY) {
                        val h = rock.rect.height()
                        rock.rect.bottom = groundCollisionY
                        rock.rect.top = groundCollisionY - h
                        rock.velocityY = 0f
                    }
                }
            }
        }

        val saveLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        
        // Draw dashed separator line instead of solid backgrounds
        val dashedPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
            alpha = 150
        }
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), dashedPaint)

        playersPose.forEach { (id, _) ->
            drawHumanSilhouette(canvas, id, { x ->
                var sx = (x + (if(id==1) 0f else 0.5f)) * scale + canvasOffsetX
                if (isFrontCamera) sx = width - sx
                sx
            }, { y -> y * scale + canvasOffsetY }, maskPaint)
        }
        canvas.restoreToCount(saveLayer)

        if (currentGameMode == GameMode.KESATRIA) {
            val groundHeight = height * 0.15f
            canvas.drawBitmap(groundBitmap, null, RectF(0f, height - groundHeight, width.toFloat(), height.toFloat()), null)
            rocks.forEach { rock ->
                if (!rock.isDestroyed) {
                    val bitmapIdx = if (rock.hits < 3) rock.hits else 2
                    canvas.drawBitmap(rockBitmaps[bitmapIdx], null, rock.rect, null)
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
                drawHands(canvas, id, tx, ty, sw * 0.48f)
                drawHead(canvas, id, tx, ty, sw * 0.95f)
            }
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

        when (currentGameMode) {
            GameMode.DANCE -> drawDanceOverlay(canvas)
            GameMode.FIGHTER -> drawFighterOverlay(canvas)
            else -> {}
        }

        if (currentGameMode == GameMode.KESATRIA && rocks.any { !it.isDestroyed && it.rect.bottom < height * 0.85f }) postInvalidateOnAnimation()
        if (currentGameMode == GameMode.FIGHTER && projectiles.isNotEmpty()) postInvalidateOnAnimation()
        if (particles.isNotEmpty()) postInvalidateOnAnimation()
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
            color = Color.WHITE
            textSize = 50f
            isFakeBoldText = true
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            typeface = gameTypeface
        }
        
        canvas.drawText(nameP1, 50f, 80f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(nameP2, width - 50f, 80f, paint)
        
        paint.textSize = 80f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(scoreP1.toString(), 50f, 160f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(scoreP2.toString(), width - 50f, 160f, paint)

        if (currentGameMode == GameMode.FIGHTER) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.WHITE
            canvas.drawRect(50f, 180f, 350f, 210f, paint)
            canvas.drawRect(width - 350f, 180f, width - 50f, 210f, paint)
            
            paint.style = Paint.Style.FILL
            paint.color = Color.GREEN
            canvas.drawRect(50f, 180f, 50f + (hpP1 * 3f), 210f, paint)
            canvas.drawRect(width - 50f - (hpP2 * 3f), 180f, width - 50f, 210f, paint)
        }

        winner?.let {
            val winPaint = Paint().apply {
                color = Color.YELLOW
                textSize = 120f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(15f, 0f, 0f, Color.RED)
                typeface = gameTypeface
            }
            canvas.drawText("$it WINS!", width / 2f, height / 2f, winPaint)
        }
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
        
        val limbStroke = shoulderWidth * 0.85f
        val silhouettePaint = Paint(paint).apply {
            strokeWidth = limbStroke
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.FILL_AND_STROKE
        }

        val torsoPath = Path()
        torsoPath.moveTo(tx(ls.x), ty(ls.y))
        torsoPath.lineTo(tx(rs.x), ty(rs.y))
        if (rh != null) torsoPath.lineTo(tx(rh.x), ty(rh.y))
        if (lh != null) torsoPath.lineTo(tx(lh.x), ty(lh.y))
        torsoPath.close()
        canvas.drawPath(torsoPath, silhouettePaint)

        val nose = getPos(id, PoseLandmark.NOSE)
        val le = getPos(id, PoseLandmark.LEFT_EAR)
        val re = getPos(id, PoseLandmark.RIGHT_EAR)
        if (nose != null) {
            val headRadius = shoulderWidth * 0.6f
            canvas.drawCircle(tx(nose.x), ty(nose.y), headRadius, silhouettePaint)
            
            val midShoulderX = (tx(ls.x) + tx(rs.x)) / 2f
            val midShoulderY = (ty(ls.y) + ty(rs.y)) / 2f
            silhouettePaint.strokeWidth = limbStroke * 0.8f
            canvas.drawLine(tx(nose.x), ty(nose.y), midShoulderX, midShoulderY, silhouettePaint)
            
            if (le != null) canvas.drawLine(tx(le.x), ty(le.y), tx(ls.x), ty(ls.y), silhouettePaint)
            if (re != null) canvas.drawLine(tx(re.x), ty(re.y), tx(rs.x), ty(rs.y), silhouettePaint)
            silhouettePaint.strokeWidth = limbStroke
        }

        val connections = listOf(
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
        )
        for (conn in connections) {
            val start = getPos(id, conn.first)
            val end = getPos(id, conn.second)
            if (start != null && end != null) {
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
            canvas.withSave {
                val angle = if (isFrontCamera) {
                    Math.toDegrees(Math.atan2((rsY - lsY).toDouble(), (rsX - lsX).toDouble())).toFloat()
                } else {
                    Math.toDegrees(Math.atan2((lsY - rsY).toDouble(), (lsX - rsX).toDouble())).toFloat()
                }
                canvas.rotate(angle, midShoulderX, midShoulderY)
                if (isFrontCamera) canvas.scale(-1f, 1f, midShoulderX, midShoulderY)
                val rect = RectF(midShoulderX - bodyWidth / 2, midShoulderY - bodyHeight * 0.25f, midShoulderX + bodyWidth / 2, midShoulderY + bodyHeight * 0.75f)
                canvas.drawBitmap(bodyBitmap, null, rect, null)
            }
        }
    }

    private fun drawHands(canvas: Canvas, id: Int, tx: (Float) -> Float, ty: (Float) -> Float, handSize: Float) {
        val leftWrist = getPos(id, PoseLandmark.LEFT_WRIST)
        val rightWrist = getPos(id, PoseLandmark.RIGHT_WRIST)
        val leftElbow = getPos(id, PoseLandmark.LEFT_ELBOW)
        val rightElbow = getPos(id, PoseLandmark.RIGHT_ELBOW)
        leftWrist?.let { wrist -> drawRotatedHand(canvas, handRightBitmap, wrist, leftElbow, tx, ty, handSize) }
        rightWrist?.let { wrist -> drawRotatedHand(canvas, handLeftBitmap, wrist, rightElbow, tx, ty, handSize) }
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
                if (isFrontCamera) canvas.scale(-1f, 1f, hx, hy)
                val rect = RectF(hx - headSize / 2, hy - headSize * 0.75f, hx + headSize / 2, hy + headSize * 0.25f)
                canvas.drawBitmap(headBitmap, null, rect, null)
            }
        }
    }

    private fun drawRotatedHand(canvas: Canvas, bitmap: Bitmap, wrist: PointF, elbow: PointF?, tx: (Float) -> Float, ty: (Float) -> Float, handSize: Float) {
        val wx = tx(wrist.x)
        val wy = ty(wrist.y)
        canvas.withSave {
            if (elbow != null) {
                val ex = tx(elbow.x)
                val ey = ty(elbow.y)
                val angle = Math.toDegrees(Math.atan2((wy - ey).toDouble(), (wx - ex).toDouble())).toFloat()
                canvas.rotate(angle, wx, wy)
            }
            val rect = RectF(wx - handSize / 2, wy - handSize / 2, wx + handSize / 2, wy + handSize / 2)
            canvas.drawBitmap(bitmap, null, rect, null)
        }
    }
}
