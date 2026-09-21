package com.example.paocao

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

data class ScoringConfig(
    var alignmentWeight: Float = 0.30f,     // 排面整齐权重
    var countWeight: Float = 0.15f,         // 出勤率权重
    var loudnessWeight: Float = 0.20f,      // 口号响亮度权重
    var spacingWeight: Float = 0.15f,       // 间距评分权重
    var motionWeight: Float = 0.20f,        // 动作一致性权重

    var expectedStudents: Int = 50,
    var snrMin: Float = 10f,
    var snrMax: Float = 25f,

    // 基于人脸的新参数
    var alignmentSensitivity: Float = 2.0f, // 排面整齐灵敏度
    var sameRowThreshold: Float = 80f,      // 同排判定阈值
    var motionSensitivity: Float = 1.5f,    // 动作一致性灵敏度
    var idealSpacing: Float = 150f,
    var spacingTolerance: Float = 60f,
    var minConfidence: Float = 0.3f,        // 人脸置信度
    var calibrationSeconds: Int = 5
)

data class ScoreResult(
    val total: Float,
    val alignment: Float,
    val count: Int,
    val countScore: Float,
    val loudness: Float,
    val spacing: Float,
    val motion: Float,
    val snr: Float,
    val jerseyNumbers: List<Int> = emptyList()
)

object ScoringEngine {

    /**
     * 1. 排面整齐度（基于人脸Y坐标）
     */
    fun scoreAlignment(
        faceCenters: List<Pair<Float, Float>>,
        sensitivity: Float,
        rowThreshold: Float
    ): Float {
        if (faceCenters.size < 3) return 0f
        val sorted = faceCenters.sortedBy { it.second }
        val groups = mutableListOf<MutableList<Pair<Float, Float>>>()
        var current = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            if (abs(sorted[i].second - sorted[i - 1].second) < rowThreshold) {
                current.add(sorted[i])
            } else {
                groups.add(current)
                current = mutableListOf(sorted[i])
            }
        }
        groups.add(current)

        var totalCv = 0f
        var validGroups = 0
        for (g in groups) {
            if (g.size < 3) continue
            val xs = g.map { it.first }
            val mean = xs.average().toFloat()
            if (mean < 1e-3f) continue
            val std = sqrt(xs.map { (it - mean) * (it - mean) }.average()).toFloat()
            totalCv += std / mean
            validGroups++
        }
        if (validGroups == 0) return 0f
        val avgCv = totalCv / validGroups
        return (100f * exp(-sensitivity * avgCv)).coerceIn(0f, 100f)
    }

    /**
     * 2. 出勤率
     */
    fun scoreCount(detected: Int, expected: Int): Float {
        if (expected <= 0) return 0f
        return (detected.toFloat() / expected * 100f).coerceIn(0f, 100f)
    }

    /**
     * 3. 响亮度
     */
    fun scoreLoudness(snr: Float, snrMin: Float, snrMax: Float): Float {
        if (snr <= snrMin) return 0f
        if (snr >= snrMax) return 100f
        return (snr - snrMin) / (snrMax - snrMin) * 100f
    }

    /**
     * 4. 动作一致性（基于人脸Y坐标的起伏同步率）
     */
    fun scoreMotionConsistency(
        currentFaceYList: List<Float>,
        previousFaceYList: List<Float>?,
        sensitivity: Float
    ): Float {
        if (currentFaceYList.size < 3) return 50f

        // 当前帧所有人脸的Y坐标标准差（排面是否在一条线上）
        val meanY = currentFaceYList.average().toFloat()
        val yStd = sqrt(currentFaceYList.map { (it - meanY) * (it - meanY) }.average()).toFloat()

        // 和上一帧对比，起伏是否同步（计算Y坐标变化的方差）
        var jitter = 0f
        if (previousFaceYList != null && previousFaceYList.size == currentFaceYList.size) {
            jitter = sqrt(currentFaceYList.indices.map {
                val diff = currentFaceYList[it] - previousFaceYList[it]
                diff * diff
            }.average()).toFloat()
        }

        // 综合评分：排面越齐、起伏越同步，分数越高
        val deviation = (yStd * 0.5f) + (jitter * 0.5f)
        return (100f * exp(-sensitivity * deviation / 50f)).coerceIn(0f, 100f)
    }

    /**
     * 5. 间距评分
     */
    fun scoreSpacing(
        currentBottomY: Float,
        previousTopY: Float?,
        idealSpacing: Float,
        tolerance: Float
    ): Float {
        if (previousTopY == null) return 70f
        val actualSpacing = previousTopY - currentBottomY
        if (actualSpacing <= 0) return 0f
        val deviation = abs(actualSpacing - idealSpacing)
        return if (deviation <= tolerance) {
            100f - (deviation / tolerance) * 20f
        } else {
            (80f * exp(-0.01f * (deviation - tolerance))).coerceIn(0f, 80f)
        }
    }

    fun combine(
        alignment: Float, countScore: Float, loudness: Float,
        spacing: Float, motion: Float, cfg: ScoringConfig
    ): Float {
        val sum = cfg.alignmentWeight + cfg.countWeight + cfg.loudnessWeight +
                  cfg.spacingWeight + cfg.motionWeight
        if (sum <= 0f) return 0f
        return (alignment * cfg.alignmentWeight +
                countScore * cfg.countWeight +
                loudness * cfg.loudnessWeight +
                spacing * cfg.spacingWeight +
                motion * cfg.motionWeight) / sum
    }
}
