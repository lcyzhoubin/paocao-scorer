package com.example.paocao

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class JerseyNumberRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * 识别红色马甲上的数字
     */
    suspend fun recognize(
        bitmap: Bitmap,
        persons: List<List<Pair<Float, Float>>>
    ): List<Int> = withContext(Dispatchers.Default) {
        val numbers = mutableListOf<Int>()
        
        for (person in persons) {
            if (person.size < 12) continue
            
            // 定位胸部/腹部区域（号码通常印在这里）
            val ls = person[11]; val rs = person[12]
            val lh = person[23]; val rh = person[24]
            
            val chestX = ((ls.first + rs.first + lh.first + rh.first) / 4 * bitmap.width).toInt()
            val chestY = ((ls.second + rs.second + lh.second + rh.second) / 4 * bitmap.height).toInt()
            
            // 扩大裁切范围，确保数字完整
            val shoulderWidth = kotlin.math.abs(rs.first - ls.first) * bitmap.width
            val cropW = (shoulderWidth * 1.2f).toInt().coerceAtLeast(40)
            val cropH = (shoulderWidth * 1.5f).toInt().coerceAtLeast(50)
            
            val left = (chestX - cropW / 2).coerceIn(0, bitmap.width - 1)
            val top = (chestY - cropH / 2).coerceIn(0, bitmap.height - 1)
            val right = (left + cropW).coerceAtMost(bitmap.width)
            val bottom = (top + cropH).coerceAtMost(bitmap.height)
            
            if (right <= left || bottom <= top) continue
            
            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            
            // 👇 使用 ML Kit 进行 OCR 识别
            val image = InputImage.fromBitmap(crop, 0)
            val resultText = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        cont.resume(text.text)
                    }
                    .addOnFailureListener {
                        cont.resume("")
                    }
            }
            
            // 从识别出的文本中提取数字
            val digits = resultText.filter { it.isDigit() }
            if (digits.length in 1..3) {
                digits.toIntOrNull()?.let { numbers.add(it) }
            }
        }
        
        numbers.distinct()
    }

    fun close() {
        recognizer.close()
    }
}
