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
     * 自动寻找红马甲并识别上面的数字
     */
    suspend fun recognizeClassNumber(bitmap: Bitmap): Int? = withContext(Dispatchers.Default) {
        // 1. 寻找红色区域
        val redRegion = findRedRegion(bitmap) ?: return@withContext null
        
        // 2. 对红色区域进行裁剪
        val cropW = (redRegion.right - redRegion.left).coerceAtLeast(30)
        val cropH = (redRegion.bottom - redRegion.top).coerceAtLeast(30)
        val crop = Bitmap.createBitmap(bitmap, redRegion.left, redRegion.top, cropW, cropH)

        // 3. 识别文字
        val image = InputImage.fromBitmap(crop, 0)
        val resultText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
        
        // 4. 提取数字
        val digits = resultText.filter { it.isDigit() }
        if (digits.length in 1..2) {
            digits.toIntOrNull()
        } else {
            null
        }
    }

    /**
     * 简单扫描画面中的红色像素块，定位红马甲
     */
    private fun findRedRegion(bitmap: Bitmap): android.graphics.Rect? {
        var minX = bitmap.width; var maxX = 0
        var minY = bitmap.height; var maxY = 0
        var count = 0

        // 抽样扫描，每5个像素扫一次，提高速度
        for (x in 0 until bitmap.width step 5) {
            for (y in 0 until bitmap.height step 5) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 判断是否为鲜艳红色
                if (r > 150 && g < 100 && b < 100 && r > g * 2 && r > b * 2) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    count++
                }
            }
        }
        
        // 红色区域太小则忽略（过滤背景中的红车等）
        if (count < 20 || maxX - minX < 20 || maxY - minY < 20) return null
        return android.graphics.Rect(minX, minY, maxX, maxY)
    }

    fun close() = recognizer.close()
}
