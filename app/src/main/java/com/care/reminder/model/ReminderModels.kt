package com.care.reminder.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 提醒类型：吃药、喝水、久坐活动
 */
enum class ReminderCategory(
    val label: String,
    val emoji: String,
    val actionConfirmText: String,
    val completedBadgeText: String,
    val defaultTtsPrefix: String
) {
    MEDICINE(
        label = "吃药提醒",
        emoji = "💊",
        actionConfirmText = "我已吃药",
        completedBadgeText = "今日已吃药",
        defaultTtsPrefix = "温馨提醒，现在是吃药时间"
    ),
    WATER(
        label = "喝水提醒",
        emoji = "💧",
        actionConfirmText = "我已喝水",
        completedBadgeText = "今日已喝水",
        defaultTtsPrefix = "温馨提醒，该喝一杯温水了"
    ),
    SEDENTARY(
        label = "久坐活动",
        emoji = "🚶",
        actionConfirmText = "我已活动",
        completedBadgeText = "今日已活动",
        defaultTtsPrefix = "温馨提醒，您坐了比较久了，请起身活动一下筋骨"
    )
}

/**
 * 调度方式：
 * - FIXED_TIME：每天固定时间点（如 08:00 吃降压药）
 * - INTERVAL：每隔 N 分钟循环提醒（如每 60 分钟喝水、每 45 分钟久坐活动，支持夜间免打扰时段）
 */
enum class ScheduleType(val label: String) {
    FIXED_TIME("定点提醒"),
    INTERVAL("间隔循环")
}

/**
 * 单条提醒配置项
 */
data class ReminderItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val category: ReminderCategory,
    val scheduleType: ScheduleType,
    val fixedTime: String = "08:00",        // HH:mm，用于 FIXED_TIME
    val intervalMinutes: Int = 60,          // 间隔分钟数，用于 INTERVAL
    val activeStartHour: Int = 8,           // 间隔提醒生效开始小时（默认早 8 点）
    val activeEndHour: Int = 21,            // 间隔提醒生效结束小时（默认晚 9 点，夜间免打扰）
    val enabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val snoozeUntilMillis: Long = 0L,       // 若处于“延后10分钟”状态，记录下次响铃时间戳
    val nextTriggerAtMillis: Long = 0L      // 持久化的下一次闹钟触发时间（用于冷启动/回前台自愈巡检）
) {
    /**
     * 生成适合老年人的中文语音播报内容
     */
    fun buildSpeechText(alreadyCompletedToday: Boolean = false): String {
        if (category == ReminderCategory.MEDICINE && alreadyCompletedToday) {
            return "请注意，${title}今天已经打卡吃过了，请核对记录，不要重复吃药。"
        }
        val detail = if (subtitle.isNotBlank()) "，${subtitle}" else ""
        return "${category.defaultTtsPrefix}：${title}${detail}。完成后请点击屏幕上的${category.actionConfirmText}按钮。"
    }

    /**
     * 人类可读的时间规则说明
     */
    fun scheduleSummaryText(): String {
        return when (scheduleType) {
            ScheduleType.FIXED_TIME -> "每天 $fixedTime"
            ScheduleType.INTERVAL -> "每 ${intervalMinutes} 分钟（%02d:00-%02d:00）".format(activeStartHour, activeEndHour)
        }
    }
}

/**
 * 打卡动作状态
 */
enum class CheckInStatus(val label: String) {
    COMPLETED("已完成"),
    SNOOZED("延后10分钟")
}

/**
 * 今日打卡与防重复吃药记录
 */
data class CheckInRecord(
    val id: String = UUID.randomUUID().toString(),
    val reminderId: String,
    val reminderTitle: String,
    val reminderSubtitle: String,
    val category: ReminderCategory,
    val dateKey: String = todayDateKey(),   // yyyy-MM-dd
    val timeText: String = currentTimeText(), // HH:mm
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: CheckInStatus = CheckInStatus.COMPLETED
)

fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return sdf.format(Date(nowMillis))
}

fun currentTimeText(nowMillis: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
    return sdf.format(Date(nowMillis))
}
