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
import android.widget.Toast
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
    private var audioAnalyzer: AudioAnalyzer? = null
    private var jerseyRecognizer: JerseyNumberRecognizer? = null
    private var faceAnalyzer: FaceAnalyzer? = null
    private var scoreDb: ScoreDatabase? = null
    
    private val config = ScoringConfig()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentClassNumber = 1
    private var currentPeriod = "上午"
    private var lastSaveTime = 0L
    private val saveIntervalMs = 30000L
    private var prevFaceYList: List<Float>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayView = findViewById(R.id.overlayView)
        overlayView.config = config
        
        audioAnalyzer = AudioAnalyzer()
        jerseyRecognizer = JerseyNumberRecognizer()
        faceAnalyzer = FaceAnalyzer(this)
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
            audioAnalyzer?.calibrate(config.calibrationSeconds) { db ->
                overlayView.noiseFloorDb = db
                overlayView.hint = "校准完成，开始评分"
            }
        }
        findViewById<Button>(R.id.btnConfig).setOnClickListener {
            ConfigDialog(this, config) { overlayView.config = config }.show()
        }
        findViewById<Button>(R.id.btnClassSetup).setOnClickListener { showClassSetupDialog() }
        findViewById<Button>(R.id.btnHistory).setOnClickListener { showHistoryDialog() }
        findViewById<Button>(R.id.btnRanking).setOnClickListener { showRankingDialog() }
    }

    private fun showUsageGuide() {
        val guide = """
            【跑操评分APP 使用说明】
            ━━ 拍摄设置 ━━
            • 手机横屏，30°俯视斜侧面，高度1.5-2米
            ━━ 使用步骤 ━━
            1. 点击【班级设置】设置班级总人数
            2. 将红马甲同学置于画面中，系统会自动识别班号
            3. 点击【校准噪声】，全体安静5秒
            4. 开始跑操，屏幕实时显示评分
            ━━ 评分维度 ━━
            • 排面整齐度、出勤率、口号响亮度、间距评分、动作一致性
            ━━ 查询功能 ━━
            • 历史记录、排名查询
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("使用说明").setMessage(guide).setPositiveButton("知道了", null).show()
    }

    private fun showClassSetupDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 30, 50, 30) }
        
        val tvClass = android.widget.TextView(this).apply { text = "班级号（可手动输入，或等系统自动识别）"; textSize = 14f }
        val etClass = EditText(this).apply { setText(currentClassNumber.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        
        val tvTotal = android.widget.TextView(this).apply { text = "该班总人数（用于计算出勤率）"; textSize = 14f; setPadding(0, 20, 0, 5) }
        val etTotal = EditText(this).apply { setText(config.expectedStudents.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        
        val tvPeriod = android.widget.TextView(this).apply { text = "时段（上午/下午）"; textSize = 14f; setPadding(0, 20, 0, 5) }
        val etPeriod = EditText(this).apply { setText(currentPeriod); inputType = InputType.TYPE_CLASS_TEXT }

        layout.addView(tvClass); layout.addView(etClass)
        layout.addView(tvTotal); layout.addView(etTotal)
        layout.addView(tvPeriod); layout.addView(etPeriod)

        AlertDialog.Builder(this).setTitle("班级设置").setView(layout)
            .setPositiveButton("确定") { _, _ ->
                currentClassNumber = etClass.text.toString().toIntOrNull() ?: 1
                config.expectedStudents = etTotal.text.toString().toIntOrNull() ?: 50
                currentPeriod = etPeriod.text.toString().ifEmpty { "上午" }
                overlayView.currentClassNumber = currentClassNumber
                overlayView.currentPeriod = currentPeriod
                Toast.makeText(this, "设置: ${currentClassNumber}班 应到${config.expectedStudents}人", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("取消", null).show()
    }

    private fun showHistoryDialog() {
        val options = arrayOf("按日期+时段查询", "按班级查询", "全部记录")
        AlertDialog.Builder(this).setTitle("历史记录查询").setItems(options) { _, which ->
            when (which) {
                0 -> queryByDatePeriod()
                1 -> queryByClass()
                2 -> queryAll()
            }
        }.show()
    }

    private fun queryByDatePeriod() {
        val dates = scoreDb?.getAvailableDates() ?: emptyList()
        if (dates.isEmpty()) { showMessage("暂无记录"); return }
        val dateOptions = dates.toTypedArray()
        AlertDialog.Builder(this).setTitle("选择日期").setItems(dateOptions) { _, which ->
            val selectedDate = dateOptions[which]
            AlertDialog.Builder(this).setTitle("选择时段").setItems(arrayOf("上午", "下午")) { _, p ->
                val period = if (p == 0) "上午" else "下午"
                showRecords("$selectedDate $period", scoreDb?.getScoresByDate(selectedDate, period) ?: emptyList())
            }.show()
        }.show()
    }

    private fun queryByClass() {
        val et = EditText(this).apply { hint = "输入班级号"; inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("按班级查询").setView(et)
            .setPositiveButton("查询") { _, _ ->
                val cn = et.text.toString().toIntOrNull() ?: return@setPositiveButton
                showRecords("${cn}班 历史记录", scoreDb?.getScoresByClass(cn) ?: emptyList())
            }.setNegativeButton("取消", null).show()
    }

    private fun queryAll() { showRecords("全部记录", scoreDb?.getAllScores(50) ?: emptyList()) }

    private fun showRecords(title: String, records: List<ScoreDatabase.ScoreRecord>) {
        val sb = StringBuilder()
        if (records.isEmpty()) sb.append("暂无记录")
        else for (r in records) {
            sb.appendLine("${r.classNumber}班 ${r.date} ${r.period}")
            sb.appendLine("  总分:${"%.1f".format(r.total)} 整齐:${"%.1f".format(r.alignment)} 出勤:${r.count}人")
            sb.appendLine("  响亮:${"%.1f".format(r.loudness)} 间距:${"%.1f".format(r.spacing)} 一致:${"%.1f".format(r.motion)}")
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(sb.toString()).setPositiveButton("关闭", null).show()
    }

    private fun showRankingDialog() {
        val dates = scoreDb?.getAvailableDates() ?: emptyList()
        if (dates.isEmpty()) { showMessage("暂无记录"); return }
        val dateOptions = dates.toTypedArray()
        AlertDialog.Builder(this).setTitle("选择排名日期").setItems(dateOptions) { _, which ->
            val selectedDate = dateOptions[which]
            AlertDialog.Builder(this).setTitle("选择时段").setItems(arrayOf("上午", "下午")) { _, p ->
                val period = if (p == 0) "上午" else "下午"
                val ranking = scoreDb?.getRanking(selectedDate, period) ?: emptyList()
                val sb = StringBuilder().appendLine("$selectedDate $period 排名\n═══════════════")
                for ((index, r) in ranking.withIndex()) {
                    val medal = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}." }
                    sb.appendLine("$medal ${r.classNumber}班  总分:${"%.1f".format(r.total)}")
                }
                AlertDialog.Builder(this).setTitle("排名结果").setMessage(sb.toString()).setPositiveButton("关闭", null).show()
            }.show()
        }.show()
    }

    private fun showMessage(msg: String) { AlertDialog.Builder(this).setMessage(msg).setPositiveButton("确定", null).show() }

    private fun startAll() {
        startCamera()
        audioAnalyzer?.start()
        overlayView.hint = "正在寻找红马甲识别班号..."
        overlayView.currentClassNumber = currentClassNumber
        overlayView.currentPeriod = currentPeriod
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
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
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

      // 👇 新增：用于控制处理频率的变量
    private var frameCount = 0
    private var lastOcrTime = 0L

    private fun processFrame(imageProxy: ImageProxy) {
        // 1. 降频：每 3 帧处理一次，极大降低 CPU 负载，防止卡死
        frameCount++
        if (frameCount % 3 != 0) return

        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        scope.launch {
            try {
                // 2. 人脸检测
                val faceBoxes = faceAnalyzer?.detect(rotated) ?: emptyList()
                val faceCenters = faceBoxes.map { it.centerX to it.centerY }
                val currentFaceYList = faceBoxes.map { it.centerY }

                // 3. 自动识别红马甲班号（只在识别到人脸，且距离上次识别超过3秒时才做）
                val now = System.currentTimeMillis()
                if (faceBoxes.isNotEmpty() && now - lastOcrTime > 3000) {
                    lastOcrTime = now
                    val recognizedClass = jerseyRecognizer?.recognizeClassNumber(rotated)
                    if (recognizedClass != null && recognizedClass in 1..99) {
                        currentClassNumber = recognizedClass
                        withContext(Dispatchers.Main) {
                            overlayView.currentClassNumber = currentClassNumber
                            Toast.makeText(this@MainActivity, "自动识别到班号: ${currentClassNumber}班", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 4. 评分计算
                val alignment = ScoringEngine.scoreAlignment(faceCenters, config.alignmentSensitivity, config.sameRowThreshold)
                
                // 人数：过滤掉特别小的误检，人脸检测通常比姿态检测准
                val count = faceBoxes.size 
                val countScore = ScoringEngine.scoreCount(count, config.expectedStudents)
                
                val snr = audioAnalyzer?.getSnr() ?: 0f
                val loudness = ScoringEngine.scoreLoudness(snr, config.snrMin, config.snrMax)
                
                val motion = ScoringEngine.scoreMotionConsistency(currentFaceYList, prevFaceYList, config.motionSensitivity)
                prevFaceYList = currentFaceYList
                
                val spacing = if (faceCenters.isNotEmpty()) {
                    ScoringEngine.scoreSpacing(faceCenters.maxByOrNull { it.second }?.second ?: 0f, null, config.idealSpacing, config.spacingTolerance)
                } else 50f
                
                val total = ScoringEngine.combine(alignment, countScore, loudness, spacing, motion, config)
                val scoreResult = ScoreResult(total, alignment, count, countScore, loudness, spacing, motion, snr)

                // 5. 更新UI
                withContext(Dispatchers.Main) {
                    val peopleList = faceBoxes.map { OverlayView.Person(emptyList()) } 
                    overlayView.update(scoreResult, peopleList, emptyList(), faceCenters)
                    
                    if (now - lastSaveTime > saveIntervalMs && count > 0) {
                        lastSaveTime = now
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        scoreDb?.saveScore(currentClassNumber, date, currentPeriod, total, alignment, count, countScore, loudness, spacing, motion, snr)
                    }
                }
            } catch (e: Exception) { 
                // 防止因为单帧出错导致崩溃
            }
        }
    }
    private fun hasPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) { startAll(); showUsageGuide() }
        else { Toast.makeText(this, "需要摄像头和麦克风权限", Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        audioAnalyzer?.stop()
        jerseyRecognizer?.close()
        faceAnalyzer?.close()
        scope.cancel()
    }
}
