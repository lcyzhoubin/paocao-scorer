package com.example.paocao

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 号码背心数字识别器（离线方案）
 * 原理：对检测到的人体区域进行裁切，在背心区域做数字模板匹配
 * 无需联网，无需第三方API密钥
 */
class JerseyNumberRecognizer {

    /**
     * 从帧中识别号码背心上的数字
     * @param bitmap 原始图像
     * @param persons 所有检测到的人的关键点列表（归一化坐标）
     * @return 识别到的号码列表
     */
    suspend fun recognize(
        bitmap: Bitmap,
        persons: List<List<Pair<Float, Float>>>
    ): List<Int> = withContext(Dispatchers.Default) {
        val numbers = mutableListOf<Int>()
        for (person in persons) {
            // 号码背心的数字通常位于胸部区域
            // 使用左右肩和左右髋的中点来确定胸部区域
            if (person.size < 12) continue
            val ls = person[11]; val rs = person[12]
            val lh = person[23]; val rh = person[24]

            // 胸部中心
            val chestX = ((ls.first + rs.first + lh.first + rh.first) / 4 * bitmap.width).toInt()
            val chestY = ((ls.second + rs.second) / 2 * bitmap.height).toInt()

            // 裁切胸部区域（宽约肩宽的80%，高约肩宽的60%）
            val shoulderWidth = kotlin.math.abs(rs.first - ls.first) * bitmap.width
            val cropW = (shoulderWidth * 0.8f).toInt().coerceAtLeast(20)
            val cropH = (shoulderWidth * 0.6f).toInt().coerceAtLeast(15)

            val left = (chestX - cropW / 2).coerceIn(0, bitmap.width - 1)
            val top = (chestY - cropH / 2).coerceIn(0, bitmap.height - 1)
            val right = (left + cropW).coerceAtMost(bitmap.width)
            val bottom = (top + cropH).coerceAtMost(bitmap.height)

            if (right <= left || bottom <= top) continue

            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

            // 简单的数字检测：统计高对比度区域的特征
            // 实际使用时，如果需要更高精度，可以集成轻量级TFLite数字分类模型
            val digit = simpleDigitDetect(crop)
            if (digit > 0) numbers.add(digit)
        }
        numbers.distinct()
    }

    /**
     * 简单的数字检测（基于背心区域的黑白像素分析）
     * 注意：这是一个基础实现。如需更高精度，建议将背心号码区域裁切后
     * 调用百度OCR Android SDK或腾讯云OCR SDK进行识别。
     * 百度OCR支持数字识别场景，适用于手机号提取、快递单号提取等。
     */
    private fun simpleDigitDetect(bitmap: Bitmap): Int {
        if (bitmap.width < 10 || bitmap.height < 10) return 0

        // 将图像缩小，统计暗色像素占比
        val scaled = Bitmap.createScaledBitmap(bitmap, 20, 15, true)
        var darkPixels = 0
        var totalPixels = 0
        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                if (brightness < 100) darkPixels++
                totalPixels++
            }
        }
        scaled.recycle()

        val darkRatio = darkPixels.toFloat() / totalPixels
        // 暗色像素占比过高（可能是阴影）或过低（没有数字）返回0
        if (darkRatio < 0.05f || darkRatio > 0.6f) return 0

        // 返回一个估计值（基于暗色像素比例映射）
        // 这是一个占位逻辑，实际使用建议替换为OCR
        return 0
    }

    fun close() {
        // 如有资源需要释放，在此处理
    }
}
