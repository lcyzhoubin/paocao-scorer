package com.example.paocao

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Person(val landmarks: List<NormalizedLandmark>)

    var config: ScoringConfig = ScoringConfig()
    var noiseFloorDb: Float = -100f
    var hint: String = ""
    var jerseyNumbers: List<Int> = emptyList()
    var currentClassNumber: Int = 1
    var currentPeriod: String = "上午"

    private var result: ScoreResult? = null
    private var people: List<Person> = emptyList()

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
    private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 20f
    }

    private val connections = listOf(
        11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27,
        24 to 26, 26 to 28
    )

    fun update(score: ScoreResult, people: List<Person>, jerseys: List<Int> = emptyList()) {
        this.result = score
        this.people = people
        this.jerseyNumbers = jerseys
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()

        // 绘制骨架
        for (p in people) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.GREEN
            for ((a, b) in connections) {
                val la = p.landmarks.getOrNull(a) ?: continue
                val lb = p.landmarks.getOrNull(b) ?: continue
                if (la.visibility().orElse(0f) < config.minConfidence) continue
                if (lb.visibility().orElse(0f) < config.minConfidence) continue
                canvas.drawLine(la.x() * w, la.y() * h, lb.x() * w, lb.y() * h, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.YELLOW
            for (lm in p.landmarks) {
                if (lm.visibility().orElse(0f) < config.minConfidence) continue
                canvas.drawCircle(lm.x() * w, lm.y() * h, 5f, paint)
            }
        }

        val r = result ?: return

        // 评分面板
        val panel = Paint().apply { color = Color.argb(170, 0, 0, 0) }
        canvas.drawRect(20f, 20f, 620f, 520f, panel)

        // 班级信息
        canvas.drawText("班级: ${currentClassNumber}班  ${currentPeriod}", 40f, 60f, smallPaint)

        // 总分
        canvas.drawText("总分: %.1f".format(r.total), 40f, 110f, bigPaint)

        // 各项子分
        canvas.drawText("动作整齐: %.1f".format(r.alignment), 40f, 165f, midPaint)
        canvas.drawText("出勤率: %d人/%.0f分".format(r.count, r.countScore), 40f, 210f, midPaint)
        canvas.drawText("口号响亮: %.1f".format(r.loudness), 40f, 255f, midPaint)
        canvas.drawText("间距评分: %.1f".format(r.spacing), 40f, 300f, midPaint)
        canvas.drawText("动作一致: %.1f".format(r.motion), 40f, 345f, midPaint)

        // 详细数据
        canvas.drawText("信噪比: %.1f dB".format(r.snr), 40f, 395f, smallPaint)
        canvas.drawText("噪声基线: %.1f dB".format(noiseFloorDb), 40f, 430f, smallPaint)

        if (jerseyNumbers.isNotEmpty()) {
            canvas.drawText("识别号码: ${jerseyNumbers.joinToString(",")}", 40f, 470f, smallPaint)
        }

        // 提示信息
        if (hint.isNotEmpty()) {
            bigPaint.color = Color.RED
            canvas.drawText(hint, 40f, h - 60f, bigPaint)
            bigPaint.color = Color.YELLOW
        }
    }
}
