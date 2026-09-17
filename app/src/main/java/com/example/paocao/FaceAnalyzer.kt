package com.example.paocao

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FaceAnalyzer(context: Context) {

    // 定义用于传递人脸位置的数据结构
    data class FaceBox(
        val centerX: Float,
        val centerY: Float
    )

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.05f) // 允许检测较小的人脸
            .build()
    )

    /**
     * 使用 ML Kit 检测画面中的人脸数量
     */
    suspend fun detect(bitmap: Bitmap): List<FaceBox> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val result = faces.map { face ->
                        val bounds = face.boundingBox
                        FaceBox(
                            centerX = bounds.exactCenterX(),
                            centerY = bounds.exactCenterY()
                        )
                    }
                    cont.resume(result)
                }
                .addOnFailureListener {
                    cont.resume(emptyList())
                }
        }
    }

    fun close() {
        detector.close()
    }
}
