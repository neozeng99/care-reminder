package com.care.reminder.data

import android.content.Context
import com.care.reminder.model.CheckInRecord
import com.care.reminder.model.CheckInStatus
import com.care.reminder.model.ReminderCategory
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.ScheduleType
import com.care.reminder.model.currentTimeText
import com.care.reminder.model.todayDateKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化仓库（基于 SharedPreferences + JSON，零外部编译依赖，稳定可靠）
 * 负责管理：
 * 1. 提醒任务列表（吃药、喝水、久坐活动）
 * 2. 今日打卡记录与防重复吃药状态查询
 */
class ReminderRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取所有提醒项；首次启动自动初始化长辈常用的吃药、喝水、久坐默认方案
     */
    fun getReminders(): List<ReminderItem> {
        val raw = prefs.getString(KEY_REMINDERS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultReminders()
            saveReminders(defaults)
            return defaults
        }
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(jsonToReminder(array.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            val defaults = defaultReminders()
            saveReminders(defaults)
            defaults
        }
    }

    fun getReminderById(id: String): ReminderItem? {
        return getReminders().firstOrNull { it.id == id }
    }

    fun saveReminders(items: List<ReminderItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(reminderToJson(item))
        }
        prefs.edit().putString(KEY_REMINDERS, array.toString()).apply()
    }

    fun upsertReminder(item: ReminderItem): List<ReminderItem> {
        val current = getReminders().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        saveReminders(current)
        return current
    }

    fun deleteReminder(reminderId: String): List<ReminderItem> {
        val updated = getReminders().filterNot { it.id == reminderId }
        saveReminders(updated)
        return updated
    }

    fun updateSnoozeUntil(reminderId: String, snoozeUntilMillis: Long): List<ReminderItem> {
        val current = getReminders().map { item ->
            if (item.id == reminderId) item.copy(snoozeUntilMillis = snoozeUntilMillis) else item
        }
        saveReminders(current)
        return current
    }

    /**
     * 持久化保存某条提醒的下一次触发时间戳（供澎湃OS/MIUI进程被杀后冷启动自愈检查）
     */
    fun updateNextTriggerMillis(
        reminderId: String,
        nextTriggerAtMillis: Long,
        snoozeUntilMillis: Long? = null
    ): List<ReminderItem> {
        val current = getReminders().map { item ->
            if (item.id == reminderId) {
                item.copy(
                    nextTriggerAtMillis = nextTriggerAtMillis,
                    snoozeUntilMillis = snoozeUntilMillis ?: item.snoozeUntilMillis
                )
            } else {
                item
            }
        }
        saveReminders(current)
        return current
    }

    /**
     * 获取打卡历史记录（自动保留最近 14 天记录，避免无限膨胀）
     */
    fun getCheckInRecords(): List<CheckInRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(jsonToRecord(array.getJSONObject(i)))
                }
            }.sortedByDescending { it.timestampMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getTodayRecords(dateKey: String = todayDateKey()): List<CheckInRecord> {
        return getCheckInRecords().filter { it.dateKey == dateKey }
    }

    /**
     * 查询某条提醒今天已完成（COMPLETED）的所有记录
     * 对“吃药提醒”至关重要：用于清楚展示“今天 08:12 已吃过”，防止老人忘记而重复服药
     */
    fun getTodayCompletedRecordsFor(reminderId: String, dateKey: String = todayDateKey()): List<CheckInRecord> {
        return getTodayRecords(dateKey).filter {
            it.reminderId == reminderId && it.status == CheckInStatus.COMPLETED
        }
    }

    /**
     * 记录一次“已完成”或“延后10分钟”
     */
    fun recordAction(
        reminder: ReminderItem,
        status: CheckInStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): CheckInRecord {
        val newRecord = CheckInRecord(
            reminderId = reminder.id,
            reminderTitle = reminder.title,
            reminderSubtitle = reminder.subtitle,
            category = reminder.category,
            dateKey = todayDateKey(nowMillis),
            timeText = currentTimeText(nowMillis),
            timestampMillis = nowMillis,
            status = status
        )
        val cutoffMillis = nowMillis - 14L * 24 * 3600 * 1000L
        val updated = (listOf(newRecord) + getCheckInRecords())
            .filter { it.timestampMillis >= cutoffMillis }
            .sortedByDescending { it.timestampMillis }

        saveCheckInRecords(updated)
        return newRecord
    }

    /**
     * 撤销某条误触的打卡记录
     */
    fun deleteCheckInRecord(recordId: String): List<CheckInRecord> {
        val updated = getCheckInRecords().filterNot { it.id == recordId }
        saveCheckInRecords(updated)
        return updated
    }

    private fun saveCheckInRecords(records: List<CheckInRecord>) {
        val array = JSONArray()
        records.forEach { array.put(recordToJson(it)) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun reminderToJson(item: ReminderItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("subtitle", item.subtitle)
            put("category", item.category.name)
            put("scheduleType", item.scheduleType.name)
            put("fixedTime", item.fixedTime)
            put("intervalMinutes", item.intervalMinutes)
            put("activeStartHour", item.activeStartHour)
            put("activeEndHour", item.activeEndHour)
            put("enabled", item.enabled)
            put("voiceEnabled", item.voiceEnabled)
            put("snoozeUntilMillis", item.snoozeUntilMillis)
            put("nextTriggerAtMillis", item.nextTriggerAtMillis)
        }
    }

    private fun jsonToReminder(obj: JSONObject): ReminderItem {
        val categoryName = obj.optString("category", ReminderCategory.MEDICINE.name)
        val scheduleName = obj.optString("scheduleType", ScheduleType.FIXED_TIME.name)
        return ReminderItem(
            id = obj.optString("id"),
            title = obj.optString("title", "吃药提醒"),
            subtitle = obj.optString("subtitle", ""),
            category = runCatching { ReminderCategory.valueOf(categoryName) }.getOrDefault(ReminderCategory.MEDICINE),
            scheduleType = runCatching { ScheduleType.valueOf(scheduleName) }.getOrDefault(ScheduleType.FIXED_TIME),
            fixedTime = obj.optString("fixedTime", "08:00"),
            intervalMinutes = obj.optInt("intervalMinutes", 60),
            activeStartHour = obj.optInt("activeStartHour", 8),
            activeEndHour = obj.optInt("activeEndHour", 21),
            enabled = obj.optBoolean("enabled", true),
            voiceEnabled = obj.optBoolean("voiceEnabled", true),
            snoozeUntilMillis = obj.optLong("snoozeUntilMillis", 0L),
            nextTriggerAtMillis = obj.optLong("nextTriggerAtMillis", 0L)
        )
    }

    private fun recordToJson(record: CheckInRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("reminderId", record.reminderId)
            put("reminderTitle", record.reminderTitle)
            put("reminderSubtitle", record.reminderSubtitle)
            put("category", record.category.name)
            put("dateKey", record.dateKey)
            put("timeText", record.timeText)
            put("timestampMillis", record.timestampMillis)
            put("status", record.status.name)
        }
    }

    private fun jsonToRecord(obj: JSONObject): CheckInRecord {
        val categoryName = obj.optString("category", ReminderCategory.MEDICINE.name)
        val statusName = obj.optString("status", CheckInStatus.COMPLETED.name)
        return CheckInRecord(
            id = obj.optString("id"),
            reminderId = obj.optString("reminderId"),
            reminderTitle = obj.optString("reminderTitle"),
            reminderSubtitle = obj.optString("reminderSubtitle", ""),
            category = runCatching { ReminderCategory.valueOf(categoryName) }.getOrDefault(ReminderCategory.MEDICINE),
            dateKey = obj.optString("dateKey", todayDateKey()),
            timeText = obj.optString("timeText", "08:00"),
            timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
            status = runCatching { CheckInStatus.valueOf(statusName) }.getOrDefault(CheckInStatus.COMPLETED)
        )
    }

    companion object {
        private const val PREFS_NAME = "care_reminder_store"
        private const val KEY_REMINDERS = "reminders_json"
        private const val KEY_RECORDS = "checkin_records_json"

        fun defaultReminders(): List<ReminderItem> = listOf(
            ReminderItem(
                id = "med-morning",
                title = "早餐后吃降压药",
                subtitle = "1片 · 温水送服（吃过请点下方按钮防忘）",
                category = ReminderCategory.MEDICINE,
                scheduleType = ScheduleType.FIXED_TIME,
                fixedTime = "08:00",
                enabled = true,
                voiceEnabled = true
            ),
            ReminderItem(
                id = "med-noon",
                title = "午餐后吃药",
                subtitle = "按医嘱服用 · 饭后半小时",
                category = ReminderCategory.MEDICINE,
                scheduleType = ScheduleType.FIXED_TIME,
                fixedTime = "12:30",
                enabled = true,
                voiceEnabled = true
            ),
            ReminderItem(
                id = "med-evening",
                title = "晚餐后吃药/钙片",
                subtitle = "温水送服 · 睡前勿忘",
                category = ReminderCategory.MEDICINE,
                scheduleType = ScheduleType.FIXED_TIME,
                fixedTime = "19:00",
                enabled = true,
                voiceEnabled = true
            ),
            ReminderItem(
                id = "water-hourly",
                title = "喝一杯温开水",
                subtitle = "约 150~200 毫升，小口慢饮润嗓护血管",
                category = ReminderCategory.WATER,
                scheduleType = ScheduleType.INTERVAL,
                intervalMinutes = 60,
                activeStartHour = 8,
                activeEndHour = 21,
                enabled = true,
                voiceEnabled = true
            ),
            ReminderItem(
                id = "sedentary-move",
                title = "久坐起身活动筋骨",
                subtitle = "站起来走动 5 分钟，转转肩膀、活动膝盖",
                category = ReminderCategory.SEDENTARY,
                scheduleType = ScheduleType.INTERVAL,
                intervalMinutes = 45,
                activeStartHour = 9,
                activeEndHour = 20,
                enabled = true,
                voiceEnabled = true
            )
        )
    }
}
