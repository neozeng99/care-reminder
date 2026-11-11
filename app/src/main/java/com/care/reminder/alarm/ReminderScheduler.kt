package com.care.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.ScheduleType
import java.util.Calendar

/**
 * 精确闹钟调度器
 * 支持：
 * 1. 每天定点提醒（如 08:00 吃药）
 * 2. 间隔循环提醒（如每 60 分钟喝水、每 45 分钟久坐活动，自动避开夜间免打扰时段）
 * 3. 延后 10 分钟（Snooze）再次响铃
 */
object ReminderScheduler {

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"

    /**
     * 检查当前是否拥有 Android 12+ 精确闹钟权限
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 为单条提醒注册下一次闹钟
     */
    fun scheduleReminder(context: Context, item: ReminderItem, nowMillis: Long = System.currentTimeMillis()): Long? {
        cancelReminder(context, item.id)
        if (!item.enabled) return null

        val triggerAtMillis = computeNextTriggerMillis(item, nowMillis)
        setExactAlarm(
            context = context,
            reminderId = item.id,
            triggerAtMillis = triggerAtMillis,
            isSnooze = false,
            requestCode = requestCodeFor(item.id, isSnooze = false)
        )
        return triggerAtMillis
    }

    /**
     * 延后 N 分钟提醒（默认 10 分钟）
     */
    fun scheduleSnooze(
        context: Context,
        item: ReminderItem,
        minutes: Int = 10,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val triggerAtMillis = nowMillis + minutes * 60_000L
        val repo = ReminderRepository(context)
        repo.updateSnoozeUntil(item.id, triggerAtMillis)

        setExactAlarm(
            context = context,
            reminderId = item.id,
            triggerAtMillis = triggerAtMillis,
            isSnooze = true,
            requestCode = requestCodeFor(item.id, isSnooze = true)
        )
        return triggerAtMillis
    }

    /**
     * 取消某条提醒的常规闹钟与延后闹钟
     */
    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(false, true).forEach { isSnooze ->
            val pendingIntent = buildPendingIntent(
                context = context,
                reminderId = reminderId,
                isSnooze = isSnooze,
                requestCode = requestCodeFor(reminderId, isSnooze)
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    /**
     * 重新注册所有已开启的提醒（应用启动或手机重启后调用）
     */
    fun rescheduleAll(context: Context) {
        val repo = ReminderRepository(context)
        val now = System.currentTimeMillis()
        repo.getReminders().forEach { item ->
            if (item.enabled) {
                scheduleReminder(context, item, now)
                if (item.snoozeUntilMillis > now) {
                    setExactAlarm(
                        context = context,
                        reminderId = item.id,
                        triggerAtMillis = item.snoozeUntilMillis,
                        isSnooze = true,
                        requestCode = requestCodeFor(item.id, isSnooze = true)
                    )
                }
            } else {
                cancelReminder(context, item.id)
            }
        }
    }

    /**
     * 计算下一次常规触发时间
     */
    fun computeNextTriggerMillis(item: ReminderItem, nowMillis: Long = System.currentTimeMillis()): Long {
        return when (item.scheduleType) {
            ScheduleType.FIXED_TIME -> computeNextFixedTimeMillis(item.fixedTime, nowMillis)
            ScheduleType.INTERVAL -> computeNextIntervalMillis(
                intervalMinutes = item.intervalMinutes.coerceAtLeast(5),
                activeStartHour = item.activeStartHour.coerceIn(0, 23),
                activeEndHour = item.activeEndHour.coerceIn(1, 24),
                nowMillis = nowMillis
            )
        }
    }

    private fun computeNextFixedTimeMillis(fixedTime: String, nowMillis: Long): Long {
        val parts = fixedTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun computeNextIntervalMillis(
        intervalMinutes: Int,
        activeStartHour: Int,
        activeEndHour: Int,
        nowMillis: Long
    ): Long {
        val candidateMillis = nowMillis + intervalMinutes * 60_000L
        val candidate = Calendar.getInstance().apply {
            timeInMillis = candidateMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val candidateHour = candidate.get(Calendar.HOUR_OF_DAY)
        if (candidateHour < activeStartHour) {
            candidate.set(Calendar.HOUR_OF_DAY, activeStartHour)
            candidate.set(Calendar.MINUTE, 0)
            return candidate.timeInMillis
        }
        if (candidateHour >= activeEndHour) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
            candidate.set(Calendar.HOUR_OF_DAY, activeStartHour)
            candidate.set(Calendar.MINUTE, 0)
            return candidate.timeInMillis
        }
        return candidate.timeInMillis
    }

    private fun setExactAlarm(
        context: Context,
        reminderId: String,
        triggerAtMillis: Long,
        isSnooze: Boolean,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminderId, isSnooze, requestCode)

        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun buildPendingIntent(
        context: Context,
        reminderId: String,
        isSnooze: Boolean,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.care.reminder.ACTION_TRIGGER_$reminderId"
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeFor(reminderId: String, isSnooze: Boolean): Int {
        val base = reminderId.hashCode() and 0x0FFFFFFF
        return if (isSnooze) base + 100_000 else base
    }
}
