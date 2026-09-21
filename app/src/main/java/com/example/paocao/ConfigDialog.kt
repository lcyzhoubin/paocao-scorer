package com.example.paocao

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

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
            "排面整齐权重 (如0.30)" to config.alignmentWeight.toString(),
            "出勤率权重 (如0.15)" to config.countWeight.toString(),
            "响亮度权重 (如0.20)" to config.loudnessWeight.toString(),
            "间距评分权重 (如0.15)" to config.spacingWeight.toString(),
            "动作一致权重 (如0.20)" to config.motionWeight.toString(),
            "应到人数 (如50)" to config.expectedStudents.toString(),
            "信噪比下限dB (如10)" to config.snrMin.toString(),
            "信噪比上限dB (如25)" to config.snrMax.toString(),
            "排面整齐灵敏度 (越大越严)" to config.alignmentSensitivity.toString(),
            "动作一致性灵敏度 (越大越严)" to config.motionSensitivity.toString(),
            "理想间距像素 (如150)" to config.idealSpacing.toString(),
            "间距容差像素 (如60)" to config.spacingTolerance.toString(),
            "校准噪声时长秒 (如5)" to config.calibrationSeconds.toString()
        )

        val edits = mutableListOf<EditText>()
        for ((label, value) in fields) {
            // 添加文字标签
            val tv = TextView(context).apply {
                text = label
                textSize = 14f
                setPadding(0, 15, 0, 5)
            }
            layout.addView(tv)

            // 添加输入框
            val et = EditText(context).apply {
                setText(value)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            layout.addView(et)
            edits.add(et)
        }

        AlertDialog.Builder(context)
            .setTitle("评分参数配置（所有值修改后立即生效）")
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
                config.motionSensitivity = edits[9].text.toString().toFloatOrNull() ?: config.motionSensitivity
                config.idealSpacing = edits[10].text.toString().toFloatOrNull() ?: config.idealSpacing
                config.spacingTolerance = edits[11].text.toString().toFloatOrNull() ?: config.spacingTolerance
                config.calibrationSeconds = edits[12].text.toString().toIntOrNull() ?: config.calibrationSeconds
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
