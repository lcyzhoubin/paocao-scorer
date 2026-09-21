package com.example.paocao

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FaceAnalyzer(context: Context) {

    data class FaceBox(val centerX: Float, val centerY: Float)

    // 👇 开启精准模式，允许检测更小的脸，提高人数统计精度
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // 改为精准模式
            .setMinFaceSize(0.02f) // 允许检测非常小的脸（远距离的）
            .enableTracking()
            .build()
    )

    suspend fun detect(bitmap: Bitmap): List<FaceBox> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val result = faces.map { face ->
                        val bounds = face.boundingBox
                        FaceBox(centerX = bounds.exactCenterX(), centerY = bounds.exactCenterY())
                    }
                    cont.resume(result)
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
    }

    fun close() = detector.close()
}
