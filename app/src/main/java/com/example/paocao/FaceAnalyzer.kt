package com.example.paocao

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

class FaceAnalyzer(context: Context) {
    private val detector: FaceDetector

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_detection_short_range.task")
            .setDelegate(Delegate.CPU)
            .build()

        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.3f)
            .build()

        detector = FaceDetector.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): Int {
        val result: FaceDetectorResult = detector.detect(BitmapImageBuilder(bitmap).build())
        return result.detections().size
    }

    fun close() = detector.close()
}
