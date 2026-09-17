package com.example.paocao

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlin.math.abs
import kotlin.math.sqrt

class FaceAnalyzer(context: Context) {
    private val detector: FaceDetector

    data class FaceBox(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val centerX: Float, val centerY: Float
    )

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_detection_short_range.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.2f)
            .build()

        detector = FaceDetector.createFromOptions(context, options)
    }

    /**
     * 网格切片检测法：将画面切成3x3块，分别检测，提高密集人群检出率
     */
    fun detect(bitmap: Bitmap): List<FaceBox> {
        val tileWidth = bitmap.width / 3
        val tileHeight = bitmap.height / 3
        val rawFaces = mutableListOf<FaceBox>()

        for (row in 0..2) {
            for (col in 0..2) {
                // 切片，加一点重叠区域防止边缘人脸被切断
                val left = (col * tileWidth - 20).coerceAtLeast(0)
                val top = (row * tileHeight - 20).coerceAtLeast(0)
                val right = ((col + 1) * tileWidth + 20).coerceAtMost(bitmap.width)
                val bottom = ((row + 1) * tileHeight + 20).coerceAtMost(bitmap.height)

                val tile = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val result: FaceDetectorResult = detector.detect(BitmapImageBuilder(tile).build())

                result.detections().forEach { detection ->
                    val box = detection.boundingBox()
                    // 坐标映射回全局画面
                    rawFaces.add(
                        FaceBox(
                            x = box.left + left,
                            y = box.top + top,
                            width = box.width(),
                            height = box.height(),
                            centerX = box.centerX() + left,
                            centerY = box.centerY() + top
                        )
                    )
                }
                tile.recycle()
            }
        }
        return mergeOverlappingFaces(rawFaces)
    }

    /**
     * 去重：合并距离太近的人脸框（同一个人被多次检测到）
     */
    private fun mergeOverlappingFaces(faces: List<FaceBox>): List<FaceBox> {
        val merged = mutableListOf<FaceBox>()
        val threshold = 40f // 两个中心点距离小于40像素视为同一人
        
        for (face in faces) {
            var isDuplicate = false
            for (existing in merged) {
                val dx = face.centerX - existing.centerX
                val dy = face.centerY - existing.centerY
                if (sqrt(dx * dx + dy * dy) < threshold) {
                    isDuplicate = true
                    break
                }
            }
            if (!isDuplicate) {
                merged.add(face)
            }
        }
        return merged
    }

    fun close() = detector.close()
}
