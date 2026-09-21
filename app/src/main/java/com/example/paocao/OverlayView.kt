package com.example.paocao

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 仅用于占位，不再需要复杂骨架
    data class Person(val landmarks: List<Any>)

    var config: ScoringConfig = ScoringConfig()
    var noiseFloorDb: Float = -100f
    var hint: String = ""
    var jerseyNumbers: List<Int> = emptyList()
    var currentClassNumber: Int = 1
    var currentPeriod: String = "上午"

    private var result: ScoreResult? = null
    private var people: List<Person> = emptyList()
    private var faceCenters: List<Pair<Float, Float>> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; textSize = 42f; typeface = Typeface.DEFAULT_BOLD
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN; textSize = 30f
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY; textSize = 24f
    }

    fun update(score: ScoreResult, people: List<Person>, jerseys: List<Int> = emptyList(), faceCenters: List<Pair<Float, Float>> = emptyList()) {
        this.result = score
        this.people = people
        this.jerseyNumbers = jerseys
        this.faceCenters = faceCenters
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()

        // 绘制人脸框（绿色的圈）
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.GREEN
        for ((cx, cy) in faceCenters) {
            // 因为传入的是归一化坐标，需要乘上宽高
            canvas.drawCircle(cx * w, cy * h, 25f, paint)
        }

        val r = result ?: return

        // 评分面板
        val panel = Paint().apply { color = Color.argb(170, 0, 0, 0) }
        canvas.drawRect(20f, 20f, 620f, 520f, panel)

        canvas.drawText("班级: ${currentClassNumber}班  ${currentPeriod}", 40f, 60f, smallPaint)
        canvas.drawText("总分: %.1f".format(r.total), 40f, 110f, bigPaint)
        canvas.drawText("排面整齐: %.1f".format(r.alignment), 40f, 165f, midPaint)
        canvas.drawText("出勤率: %d人/%.0f分".format(r.count, r.countScore), 40f, 210f, midPaint)
        canvas.drawText("口号响亮: %.1f".format(r.loudness), 40f, 255f, midPaint)
        canvas.drawText("间距评分: %.1f".format(r.spacing), 40f, 300f, midPaint)
        canvas.drawText("动作一致: %.1f".format(r.motion), 40f, 345f, midPaint)

        canvas.drawText("信噪比: %.1f dB".format(r.snr), 40f, 395f, smallPaint)
        canvas.drawText("噪声基线: %.1f dB".format(noiseFloorDb), 40f, 430f, smallPaint)
        canvas.drawText("识别到人脸数量: %d".format(r.count), 40f, 470f, smallPaint)

        if (hint.isNotEmpty()) {
            bigPaint.color = Color.RED
            canvas.drawText(hint, 40f, h - 60f, bigPaint)
            bigPaint.color = Color.YELLOW
        }
    }
}
