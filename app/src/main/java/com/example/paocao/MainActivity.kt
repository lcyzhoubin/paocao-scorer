package com.example.paocao

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var overlayView: OverlayView
    private lateinit var poseAnalyzer: PoseAnalyzer
    private lateinit var audioAnalyzer: AudioAnalyzer
    private lateinit var jerseyRecognizer: JerseyNumberRecognizer
    private lateinit var scoreDb: ScoreDatabase
    private val config = ScoringConfig()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentClassNumber = 1
    private var currentPeriod = "上午"
    private var lastSaveTime = 0L
    private val saveIntervalMs = 30000L
    private var previousLandmarks: List<List<Pair<Float, Float>>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayView = findViewById(R.id.overlayView)
        overlayView.config = config
        poseAnalyzer = PoseAnalyzer(this)
        audioAnalyzer = AudioAnalyzer()
        jerseyRecognizer = JerseyNumberRecognizer()
        scoreDb = ScoreDatabase(this)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        currentPeriod = if (hour < 12) "上午" else "下午"
        overlayView.currentPeriod = currentPeriod

        if (hasPermissions()) {
            startAll()
            showUsageGuide()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)
        }

        findViewById<Button>(R.id.btnHelp).setOnClickListener { showUsageGuide() }

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            overlayView.hint = "校准中，请保持安静..."
            audioAnalyzer.calibrate(config.calibrationSeconds) { db ->
                overlayView.noiseFloorDb = db
                overlayView.hint = "校准完成，开始评分"
            }
        }

        findViewById<Button>(R.id.btnConfig).setOnClickListener {
            ConfigDialog(this, config) { overlayView.config = config }.show()
        }

        findViewById<Button>(R.id.btnClassSetup).setOnClickListener {
            showClassSetupDialog()
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            showHistoryDialog()
        }

        findViewById<Button>(R.id.btnRanking).setOnClickListener {
            showRankingDialog()
        }
    }

    private fun showUsageGuide() {
        val guide = """
            【跑操评分APP 使用说明】

            ━━ 拍摄设置 ━━
            • 手机横屏，30°俯视斜侧面
            • 高度1.5-2米，距队伍5-8米
            • 与队伍行进方向成30-45°夹角

            ━━ 使用步骤 ━━
            1. 架好手机，打开APP授权
            2. 点击【班级设置】选择当前班级
            3. 点击【校准噪声】，全体安静5秒
            4. 开始跑操，屏幕实时显示评分
            5. 评分每30秒自动保存一次

            ━━ 评分维度（权重可调）━━
            • 动作整齐度(30%)：排面是否对齐
            • 出勤率(15%)：实到/应到人数
            • 口号响亮度(20%)：口号声信噪比
            • 间距评分(15%)：与前方班级距离
            • 动作一致性(20%)：肩部/头部动作统一

            ━━ 查询功能 ━━
            • 历史记录：按日期/班级查询
            • 排名查询：按日期+时段排名

            ━━ 参数调优 ━━
            • 灵敏度调大→评分更严格
            • 权重按管理重点调整
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("使用说明")
            .setMessage(guide)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showClassSetupDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val etClass = EditText(this).apply {
            hint = "班级号（如1）"
            setText(currentClassNumber.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etPeriod = EditText(this).apply {
            hint = "时段（上午/下午）"
            setText(currentPeriod)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        layout.addView(etClass)
        layout.addView(etPeriod)

        AlertDialog.Builder(this)
            .setTitle("班级设置")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                currentClassNumber = etClass.text.toString().toIntOrNull() ?: 1
                currentPeriod = etPeriod.text.toString().ifEmpty { "上午" }
                overlayView.currentClassNumber = currentClassNumber
                overlayView.currentPeriod = currentPeriod
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHistoryDialog() {
        val options = arrayOf("按日期+时段查询", "按班级查询", "全部记录")
        AlertDialog.Builder(this)
            .setTitle("历史记录查询")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> queryByDatePeriod()
                    1 -> queryByClass()
                    2 -> queryAll()
                }
            }
            .show()
    }

    private fun queryByDatePeriod() {
        val dates = scoreDb.getAvailableDates()
        if (dates.isEmpty()) {
            showMessage("暂无记录")
            return
        }
        val dateOptions = dates.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择日期")
            .setItems(dateOptions) { _, which ->
                val selectedDate = dateOptions[which]
                AlertDialog.Builder(this)
                    .setTitle("选择时段")
                    .setItems(arrayOf("上午", "下午")) { _, p ->
                        val period = if (p == 0) "上午" else "下午"
                        val records = scoreDb.getScoresByDate(selectedDate, period)
                        showRecords("$selectedDate $period", records)
                    }
                    .show()
            }
            .show()
    }

    private fun queryByClass() {
        val et = EditText(this).apply {
            hint = "输入班级号"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("按班级查询")
            .setView(et)
            .setPositiveButton("查询") { _, _ ->
                val cn = et.text.toString().toIntOrNull() ?: return@setPositiveButton
                val records = scoreDb.getScoresByClass(cn)
                showRecords("${cn}班 历史记录", records)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun queryAll() {
        val records = scoreDb.getAllScores(50)
        showRecords("全部记录", records)
    }

    private fun showRecords(title: String, records: List<ScoreDatabase.ScoreRecord>) {
        val sb = StringBuilder()
        if (records.isEmpty()) {
            sb.append("暂无记录")
        } else {
            for (r in records) {
                sb.appendLine("${r.classNumber}班 ${r.date} ${r.period}")
                sb.appendLine("  总分:${"%.1f".format(r.total)} 整齐:${"%.1f".format(r.alignment)} 出勤:${r.count}人")
                sb.appendLine("  响亮:${"%.1f".format(r.loudness)} 间距:${"%.1f".format(r.spacing)} 一致:${"%.1f".format(r.motion)}")
                sb.appendLine("  时间: ${r.timestamp}")
                sb.appendLine()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(sb.toString())
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showRankingDialog() {
        val dates = scoreDb.getAvailableDates()
        if (dates.isEmpty()) {
            showMessage("暂无记录")
            return
        }
        val dateOptions = dates.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择排名日期")
            .setItems(dateOptions) { _, which ->
                val selectedDate = dateOptions[which]
                AlertDialog.Builder(this)
                    .setTitle("选择时段")
                    .setItems(arrayOf("上午", "下午")) { _, p ->
                        val period = if (p == 0) "上午" else "下午"
                        val ranking = scoreDb.getRanking(selectedDate, period)
                        val sb = StringBuilder()
                        sb.appendLine("$selectedDate $period 排名")
                        sb.appendLine("═══════════════")
                        for ((index, r) in ranking.withIndex()) {
                            val medal = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${index + 1}."
                            }
                            sb.appendLine("$medal ${r.classNumber}班  总分:${"%.1f".format(r.total)}")
                            sb.appendLine("   整齐:${"%.0f".format(r.alignment)} 出勤:${r.count}人 一致:${"%.0f".format(r.motion)}")
                        }
                        AlertDialog.Builder(this)
                            .setTitle("排名结果")
                            .setMessage(sb.toString())
                            .setPositiveButton("关闭", null)
                            .show()
                    }
                    .show()
            }
            .show()
    }

    private fun showMessage(msg: String) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("确定", null).show()
    }

    private fun startAll() {
        startCamera()
        audioAnalyzer.start()
        overlayView.hint = "请先点击【校准噪声】保持5秒安静"
        overlayView.currentClassNumber = currentClassNumber
        overlayView.currentPeriod = currentPeriod
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(
                    findViewById<PreviewView>(R.id.previewView).surfaceProvider
                )
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 480))
                .build()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try { processFrame(imageProxy) } catch (e: Exception) { }
                finally { imageProxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        val result = poseAnalyzer.detect(rotated)

        val shoulderPoints = mutableListOf<Pair<Float, Float>>()
        val people = mutableListOf<OverlayView.Person>()
        val personLandmarks = mutableListOf<List<Pair<Float, Float>>>()

        result.landmarks().forEach { lms ->
            val ls = lms[11]; val rs = lms[12]
            if (ls.visibility().orElse(0f) > config.minConfidence &&
                rs.visibility().orElse(0f) > config.minConfidence) {
                shoulderPoints.add(
                    ((ls.x() + rs.x()) / 2 * rotated.width) to
                    ((ls.y() + rs.y()) / 2 * rotated.height)
                )
            }
            val personPts = lms.map { it.x() to it.y() }
            personLandmarks.add(personPts)
            people.add(OverlayView.Person(lms))
        }

        val alignment = ScoringEngine.scoreAlignment(
            shoulderPoints, config.alignmentSensitivity, config.sameRowThreshold
        )

        val count = people.size
        val countScore = ScoringEngine.scoreCount(count, config.expectedStudents)

        val snr = audioAnalyzer.getSnr()
        val loudness = ScoringEngine.scoreLoudness(snr, config.snrMin, config.snrMax)

        val motion = ScoringEngine.scoreMotionConsistency(
            previousLandmarks, personLandmarks, config.motionSensitivity
        )
        previousLandmarks = personLandmarks

        val spacing = if (shoulderPoints.isNotEmpty()) {
            val bottomY = shoulderPoints.maxByOrNull { it.second }?.second ?: 0f
            ScoringEngine.scoreSpacing(
                bottomY, null, config.idealSpacing, config.spacingTolerance
            )
        } else 50f

        val total = ScoringEngine.combine(
            alignment, countScore, loudness, spacing, motion, config
        )

        val scoreResult = ScoreResult(
            total, alignment, count, countScore, loudness, spacing, motion, snr
        )

        scope.launch {
            val jerseys = jerseyRecognizer.recognize(rotated, personLandmarks)
            runOnUiThread {
                overlayView.update(scoreResult.copy(jerseyNumbers = jerseys), people, jerseys)
            }

            val now = System.currentTimeMillis()
            if (now - lastSaveTime > saveIntervalMs && count > 0) {
                lastSaveTime = now
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                scoreDb.saveScore(
                    currentClassNumber, date, currentPeriod,
                    total, alignment, count, countScore,
                    loudness, spacing, motion, snr
                )
            }
        }
    }

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startAll()
            showUsageGuide()
        } else {
            overlayView.hint = "需要摄像头和麦克风权限"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        audioAnalyzer.stop()
        poseAnalyzer.close()
        jerseyRecognizer.close()
        scope.cancel()
    }
}
