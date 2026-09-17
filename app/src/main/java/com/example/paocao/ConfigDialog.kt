package com.example.paocao

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class ConfigDialog(
    private val context: Context,
    private val config: ScoringConfig,
    private val onSaved: () -> Unit
) {
    fun show() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val fields = listOf(
            "动作整齐权重(如0.30)" to config.alignmentWeight.toString(),
            "出勤率权重(如0.15)" to config.countWeight.toString(),
            "响亮度权重(如0.20)" to config.loudnessWeight.toString(),
            "间距评分权重(如0.15)" to config.spacingWeight.toString(),
            "动作一致权重(如0.20)" to config.motionWeight.toString(),
            "应到人数" to config.expectedStudents.toString(),
            "信噪比下限dB" to config.snrMin.toString(),
            "信噪比上限dB" to config.snrMax.toString(),
            "整齐度灵敏度(越大越严格)" to config.alignmentSensitivity.toString(),
            "同排判定阈值(像素)" to config.sameRowThreshold.toString(),
            "动作一致性灵敏度" to config.motionSensitivity.toString(),
            "理想间距(像素)" to config.idealSpacing.toString(),
            "间距容差(像素)" to config.spacingTolerance.toString(),
            "关键点置信度阈值" to config.minConfidence.toString(),
            "噪声校准时长(秒)" to config.calibrationSeconds.toString()
        )

        val edits = mutableListOf<EditText>()
        for ((label, value) in fields) {
            val et = EditText(context).apply {
                hint = label
                setText(value)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 14f
            }
            layout.addView(et)
            edits.add(et)
        }

        AlertDialog.Builder(context)
            .setTitle("评分参数配置（权重自动归一化）")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton("保存") { _, _ ->
                config.alignmentWeight = edits[0].text.toString().toFloatOrNull() ?: config.alignmentWeight
                config.countWeight = edits[1].text.toString().toFloatOrNull() ?: config.countWeight
                config.loudnessWeight = edits[2].text.toString().toFloatOrNull() ?: config.loudnessWeight
                config.spacingWeight = edits[3].text.toString().toFloatOrNull() ?: config.spacingWeight
                config.motionWeight = edits[4].text.toString().toFloatOrNull() ?: config.motionWeight
                config.expectedStudents = edits[5].text.toString().toIntOrNull() ?: config.expectedStudents
                config.snrMin = edits[6].text.toString().toFloatOrNull() ?: config.snrMin
                config.snrMax = edits[7].text.toString().toFloatOrNull() ?: config.snrMax
                config.alignmentSensitivity = edits[8].text.toString().toFloatOrNull() ?: config.alignmentSensitivity
                config.sameRowThreshold = edits[9].text.toString().toFloatOrNull() ?: config.sameRowThreshold
                config.motionSensitivity = edits[10].text.toString().toFloatOrNull() ?: config.motionSensitivity
                config.idealSpacing = edits[11].text.toString().toFloatOrNull() ?: config.idealSpacing
                config.spacingTolerance = edits[12].text.toString().toFloatOrNull() ?: config.spacingTolerance
                config.minConfidence = edits[13].text.toString().toFloatOrNull() ?: config.minConfidence
                config.calibrationSeconds = edits[14].text.toString().toIntOrNull() ?: config.calibrationSeconds
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
