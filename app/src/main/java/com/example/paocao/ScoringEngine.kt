package com.example.paocao

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 所有评分参数，可在APP内实时调整
 */
data class ScoringConfig(
    // 权重（总和自动归一化）
    var alignmentWeight: Float = 0.30f,     // 动作整齐度权重
    var countWeight: Float = 0.15f,         // 出勤率权重
    var loudnessWeight: Float = 0.20f,      // 口号响亮度权重
    var spacingWeight: Float = 0.15f,       // 间距评分权重
    var motionWeight: Float = 0.20f,        // 动作一致性权重

    // 出勤率
    var expectedStudents: Int = 50,

    // 响亮度
    var snrMin: Float = 10f,
    var snrMax: Float = 25f,

    // 整齐度（排面）
    var alignmentSensitivity: Float = 2.0f,
    var sameRowThreshold: Float = 80f,

    // 动作一致性
    var motionSensitivity: Float = 1.5f,    // 越大越严格

    // 间距评分
    var idealSpacing: Float = 150f,         // 理想间距（像素）
    var spacingTolerance: Float = 60f,      // 允许偏差范围（像素）

    // 通用
    var minConfidence: Float = 0.3f,
    var calibrationSeconds: Int = 5
)

data class ScoreResult(
    val total: Float,
    val alignment: Float,      // 排面整齐度
    val count: Int,
    val countScore: Float,     // 出勤率得分
    val loudness: Float,
    val spacing: Float,        // 间距得分
    val motion: Float,         // 动作一致性得分
    val snr: Float,
    val jerseyNumbers: List<Int> = emptyList()
)

object ScoringEngine {

    /**
     * 1. 排面整齐度评分
     * 原理：按y坐标分组（同排），组内计算x坐标的变异系数
     */
    fun scoreAlignment(
        shoulderPoints: List<Pair<Float, Float>>,
        sensitivity: Float,
        rowThreshold: Float
    ): Float {
        if (shoulderPoints.size < 3) return 0f
        val sorted = shoulderPoints.sortedBy { it.second }
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
     * 2. 出勤率评分
     * 原理：检测人数 / 应到人数 × 100
     */
    fun scoreCount(detected: Int, expected: Int): Float {
        if (expected <= 0) return 0f
        return (detected.toFloat() / expected * 100f).coerceIn(0f, 100f)
    }

    /**
     * 3. 口号响亮度评分
     * 原理：信噪比映射到0-100分
     */
    fun scoreLoudness(snr: Float, snrMin: Float, snrMax: Float): Float {
        if (snr <= snrMin) return 0f
        if (snr >= snrMax) return 100f
        return (snr - snrMin) / (snrMax - snrMin) * 100f
    }

    /**
     * 4. 动作一致性评分
     * 原理：基于关节角度余弦相似度（参考学术研究验证的方法）
     * 对肩部和髋部的关键点计算向量方向一致性
     * 抖动越小、方向越一致，得分越高
     */
    fun scoreMotionConsistency(
        previousLandmarks: List<List<Pair<Float, Float>>>?,
        currentLandmarks: List<List<Pair<Float, Float>>>,
        sensitivity: Float
    ): Float {
        if (currentLandmarks.size < 3) return 50f  // 人数太少，给中位分

        // 使用肩部向量(左肩→右肩)方向的一致性
        val shoulderVectors = mutableListOf<Pair<Float, Float>>()
        for (person in currentLandmarks) {
            if (person.size > 12) {
                val lx = person[11].first; val ly = person[11].second
                val rx = person[12].first; val ry = person[12].second
                shoulderVectors.add((rx - lx) to (ry - ly))
            }
        }

        if (shoulderVectors.size < 3) return 50f

        // 计算所有肩部向量的方向角
        val angles = shoulderVectors.map { (dx, dy) ->
            if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) 0f
            else Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        // 计算方向角的标准差（越小越一致）
        val meanAngle = angles.average().toFloat()
        val angleStd = sqrt(angles.map {
            val diff = it - meanAngle
            diff * diff
        }.average()).toFloat()

        // 如果有上一帧数据，额外计算帧间抖动
        var jitterPenalty = 0f
        if (previousLandmarks != null && previousLandmarks.size == currentLandmarks.size) {
            var totalJitter = 0f
            var count = 0
            for (i in currentLandmarks.indices) {
                val prev = previousLandmarks[i]
                val curr = currentLandmarks[i]
                if (prev.size > 12 && curr.size > 12) {
                    // 肩部关键点的帧间位移
                    val dx1 = curr[11].first - prev[11].first
                    val dy1 = curr[11].second - prev[11].second
                    val dx2 = curr[12].first - prev[12].first
                    val dy2 = curr[12].second - prev[12].second
                    totalJitter += sqrt(dx1 * dx1 + dy1 * dy1) +
                                   sqrt(dx2 * dx2 + dy2 * dy2)
                    count += 2
                }
            }
            if (count > 0) jitterPenalty = (totalJitter / count) * 0.1f
        }

        // 映射到分数：方向标准差越小、抖动越小，分数越高
        val score = 100f * exp(-sensitivity * (angleStd / 45f) - jitterPenalty)
        return score.coerceIn(0f, 100f)
    }

    /**
     * 5. 间距评分
     * 原理：计算队列中最后一个人的位置与前方队伍的距离
     * 间距太近或太远都扣分
     */
    fun scoreSpacing(
        currentQueueBottomY: Float,
        previousQueueTopY: Float?,
        idealSpacing: Float,
        tolerance: Float
    ): Float {
        if (previousQueueTopY == null) return 70f  // 没有前一个班级时给中位分

        val actualSpacing = previousQueueTopY - currentQueueBottomY
        if (actualSpacing <= 0) return 0f  // 重叠了

        val deviation = abs(actualSpacing - idealSpacing)
        if (deviation <= tolerance) {
            // 在容差范围内，满分
            return 100f - (deviation / tolerance) * 20f
        } else {
            // 超出容差，逐渐扣分
            val extraDeviation = deviation - tolerance
            return (80f * exp(-0.01f * extraDeviation)).coerceIn(0f, 80f)
        }
    }

    /**
     * 综合评分
     */
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
