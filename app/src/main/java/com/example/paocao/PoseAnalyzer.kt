package com.example.paocao

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseAnalyzer(context: Context) {
    private var landmarker: PoseLandmarker? = null
    var errorMessage: String = ""

    init {
        try {
            // 注意：去掉了强制指定 CPU 委托，让 MediaPipe 自动适配
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(20)
                .setMinPoseDetectionConfidence(0.3f)
                .setMinPosePresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .build()

            landmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            errorMessage = "模型加载失败: ${e.message}"
        }
    }

    fun detect(bitmap: Bitmap): PoseLandmarkerResult? {
        return try {
            landmarker?.detect(BitmapImageBuilder(bitmap).build())
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try { landmarker?.close() } catch (e: Exception) {}
    }
}
