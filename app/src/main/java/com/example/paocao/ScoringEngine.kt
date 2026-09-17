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
     * 4. 动作一致性评分 (升级版：基于脑袋和肩膀的Y轴起伏)
     * 跑操时，所有人的脑袋和肩膀应该同步上下起伏。
     * 我们通过计算同一帧内所有人头和肩膀Y坐标的标准差，以及跨帧的位移方差来判断。
     */
    fun scoreMotionConsistency(
        headYList: List<Float>,
        shoulderYList: List<Float>,
        previousHeadYList: List<Float>?,
        previousShoulderYList: List<Float>?,
        sensitivity: Float
    ): Float {
        if (headYList.size < 3 && shoulderYList.size < 3) return 50f // 数据不足，给中位分

        // 1. 空间一致性：当前帧，大家是不是在同一个水平线上
        val headYStdDev = if (headYList.size > 1) {
            val mean = headYList.average().toFloat()
            sqrt(headYList.map { (it - mean) * (it - mean) }.average()).toFloat()
        } else 0f

        val shoulderYStdDev = if (shoulderYList.size > 1) {
            val mean = shoulderYList.average().toFloat()
            sqrt(shoulderYList.map { (it - mean) * (it - mean) }.average()).toFloat()
        } else 0f

        // 2. 时间一致性：上一帧和这一帧，起伏节奏是否一致（计算位移方差）
        var headJitter = 0f
        var shoulderJitter = 0f
        if (previousHeadYList != null && previousHeadYList.size == headYList.size) {
            headJitter = sqrt(headYList.indices.map {
                val diff = headYList[it] - previousHeadYList[it]
                diff * diff
            }.average()).toFloat()
        }
        if (previousShoulderYList != null && previousShoulderYList.size == shoulderYList.size) {
            shoulderJitter = sqrt(shoulderYList.indices.map {
                val diff = shoulderYList[it] - previousShoulderYList[it]
                diff * diff
            }.average()).toFloat()
        }

        // 综合空间和时间偏差，映射到0-100分
        // 分母加了 scaleFactor 防止数值过小导致分数过于敏感
        val totalDeviation = (headYStdDev * 0.5f) + (shoulderYStdDev * 0.3f) + 
                            (headJitter * 0.1f) + (shoulderJitter * 0.1f)
                            
        return (100f * exp(-sensitivity * totalDeviation / 50f)).coerceIn(0f, 100f)
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
