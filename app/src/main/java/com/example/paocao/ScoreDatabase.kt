package com.example.paocao

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * 评分数据库
 * 支持按班级号、日期、上午/下午存储和查询
 */
class ScoreDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "paocao_scores.db"
        private const val DB_VERSION = 1
        private const val TABLE = "scores"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                class_number INTEGER NOT NULL,
                date TEXT NOT NULL,
                period TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                total REAL,
                alignment REAL,
                count INTEGER,
                count_score REAL,
                loudness REAL,
                spacing REAL,
                motion REAL,
                snr REAL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun saveScore(
        classNumber: Int, date: String, period: String,
        total: Float, alignment: Float, count: Int,
        countScore: Float, loudness: Float, spacing: Float,
        motion: Float, snr: Float
    ) {
        val values = ContentValues().apply {
            put("class_number", classNumber)
            put("date", date)
            put("period", period)
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("total", total)
            put("alignment", alignment)
            put("count", count)
            put("count_score", countScore)
            put("loudness", loudness)
            put("spacing", spacing)
            put("motion", motion)
            put("snr", snr)
        }
        writableDatabase.insert(TABLE, null, values)
    }

    data class ScoreRecord(
        val id: Long, val classNumber: Int, val date: String,
        val period: String, val timestamp: String, val total: Float,
        val alignment: Float, val count: Int, val countScore: Float,
        val loudness: Float, val spacing: Float, val motion: Float,
        val snr: Float
    )

    /**
     * 查询所有记录（最新在前）
     */
    fun getAllScores(limit: Int = 100): List<ScoreRecord> {
        val list = mutableListOf<ScoreRecord>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseRecord(it))
            }
        }
        return list
    }

    /**
     * 按日期+时段查询
     */
    fun getScoresByDate(date: String, period: String): List<ScoreRecord> {
        val list = mutableListOf<ScoreRecord>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE WHERE date = ? AND period = ? ORDER BY class_number ASC",
            arrayOf(date, period)
        )
        cursor.use {
            while (it.moveToNext()) list.add(parseRecord(it))
        }
        return list
    }

    /**
     * 按班级查询所有历史
     */
    fun getScoresByClass(classNumber: Int): List<ScoreRecord> {
        val list = mutableListOf<ScoreRecord>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE WHERE class_number = ? ORDER BY timestamp DESC",
            arrayOf(classNumber.toString())
        )
        cursor.use {
            while (it.moveToNext()) list.add(parseRecord(it))
        }
        return list
    }

    /**
     * 按日期+时段排名（总分降序）
     * 使用 GROUP BY 按班级分组，ORDER BY 按总分排名
     */
    fun getRanking(date: String, period: String): List<ScoreRecord> {
        val list = mutableListOf<ScoreRecord>()
        // 每个班级取该时段最高分，然后按分数排名
        val cursor = readableDatabase.rawQuery(
            """
            SELECT *, MAX(total) as max_total FROM $TABLE
            WHERE date = ? AND period = ?
            GROUP BY class_number
            ORDER BY max_total DESC
            """.trimIndent(),
            arrayOf(date, period)
        )
        cursor.use {
            while (it.moveToNext()) list.add(parseRecord(it))
        }
        return list
    }

    /**
     * 获取某班级在某日某时段的平均分
     */
    fun getClassAverage(classNumber: Int, date: String, period: String): Float {
        val cursor = readableDatabase.rawQuery(
            "SELECT AVG(total) FROM $TABLE WHERE class_number = ? AND date = ? AND period = ?",
            arrayOf(classNumber.toString(), date, period)
        )
        cursor.use {
            if (it.moveToFirst()) return it.getFloat(0)
        }
        return 0f
    }

    /**
     * 获取所有可查询的日期列表
     */
    fun getAvailableDates(): List<String> {
        val list = mutableListOf<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT date FROM $TABLE ORDER BY date DESC", null
        )
        cursor.use {
            while (it.moveToNext()) list.add(it.getString(0))
        }
        return list
    }

    private fun parseRecord(cursor: android.database.Cursor): ScoreRecord {
        return ScoreRecord(
            cursor.getLong(0), cursor.getInt(1), cursor.getString(2),
            cursor.getString(3), cursor.getString(4), cursor.getFloat(5),
            cursor.getFloat(6), cursor.getInt(7), cursor.getFloat(8),
            cursor.getFloat(9), cursor.getFloat(10), cursor.getFloat(11),
            cursor.getFloat(12)
        )
    }
    }
