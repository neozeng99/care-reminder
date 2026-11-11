package com.care.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.care.reminder.MainActivity
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.ReminderItem
import com.care.reminder.model.ScheduleType
import java.util.Calendar

/**
 * 针对小米澎湃OS 2 / MIUI 深度加固的精确闹钟调度器
 *
 * 核心加固策略：
 * 1. 优先采用 `AlarmManager.setAlarmClock()`（闹钟级最高优先级，不受 Doze 与普通精确闹钟频率限制，状态栏显示小闹钟图标保障）。
 * 2. 每次完成调度后，将 `nextTriggerAtMillis` 持久化写入本地存储。
 * 3. 提供 `verifyAndHealAll()`：App 每次冷启动或回到主界面时自动比对持久化的下次触发时间，若已过期或丢失则立即自愈补调度。
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
     * 为单条提醒注册下一次闹钟，并将下次触发时间持久化到本地
     */
    fun scheduleReminder(context: Context, item: ReminderItem, nowMillis: Long = System.currentTimeMillis()): Long? {
        val repo = ReminderRepository(context)
        cancelReminder(context, item.id, clearPersisted = false)
        if (!item.enabled) {
            repo.updateNextTriggerMillis(item.id, nextTriggerAtMillis = 0L, snoozeUntilMillis = 0L)
            return null
        }

        val triggerAtMillis = computeNextTriggerMillis(item, nowMillis)
        setAlarmClockFirst(
            context = context,
            reminderId = item.id,
            triggerAtMillis = triggerAtMillis,
            isSnooze = false,
            requestCode = requestCodeFor(item.id, isSnooze = false)
        )
        repo.updateNextTriggerMillis(item.id, nextTriggerAtMillis = triggerAtMillis)
        return triggerAtMillis
    }

    /**
     * 延后 N 分钟提醒（默认 10 分钟），并持久化延后触发时间
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

        setAlarmClockFirst(
            context = context,
            reminderId = item.id,
            triggerAtMillis = triggerAtMillis,
            isSnooze = true,
            requestCode = requestCodeFor(item.id, isSnooze = true)
        )
        return triggerAtMillis
    }

    /**
     * 5 秒后触发一次真实系统闹钟测试（用于检验小米澎湃OS锁屏后台弹窗与通知权限是否被拦截）
     */
    fun scheduleQuickTestAlarm(
        context: Context,
        item: ReminderItem,
        delaySeconds: Int = 5
    ): Long {
        val triggerAtMillis = System.currentTimeMillis() + delaySeconds * 1000L
        setAlarmClockFirst(
            context = context,
            reminderId = item.id,
            triggerAtMillis = triggerAtMillis,
            isSnooze = true,
            requestCode = requestCodeFor(item.id, isSnooze = true) + 7
        )
        return triggerAtMillis
    }

    /**
     * 取消某条提醒的常规闹钟与延后闹钟
     */
    fun cancelReminder(context: Context, reminderId: String, clearPersisted: Boolean = true) {
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
        if (clearPersisted) {
            ReminderRepository(context).updateNextTriggerMillis(
                reminderId = reminderId,
                nextTriggerAtMillis = 0L,
                snoozeUntilMillis = 0L
            )
        }
    }

    /**
     * 冷启动 / 回到主界面时的自愈巡检：
     * 检查每条启用提醒持久化的 `nextTriggerAtMillis` 是否已过期、未初始化或被系统打断，
     * 如有过期则立即重新注册 `setAlarmClock()` 并返回本次自愈修复的条数。
     */
    fun verifyAndHealAll(context: Context): Int {
        val repo = ReminderRepository(context)
        val now = System.currentTimeMillis()
        var healedCount = 0

        repo.getReminders().forEach { item ->
            if (!item.enabled) {
                if (item.nextTriggerAtMillis != 0L) {
                    cancelReminder(context, item.id, clearPersisted = true)
                }
                return@forEach
            }

            // 1. 检查常规闹钟持久化的下次触发时间是否已过期或未设置
            val expectedNext = computeNextTriggerMillis(item, now)
            val persisted = item.nextTriggerAtMillis
            val needsHeal = persisted <= now || kotlin.math.abs(persisted - expectedNext) > 60_000L

            if (needsHeal) {
                healedCount++
            }
            // 无论是否过期，都在回前台时刷新一次 setAlarmClock 确保系统闹钟队列中存在
            setAlarmClockFirst(
                context = context,
                reminderId = item.id,
                triggerAtMillis = expectedNext,
                isSnooze = false,
                requestCode = requestCodeFor(item.id, isSnooze = false)
            )
            val validSnooze = if (item.snoozeUntilMillis > now) item.snoozeUntilMillis else 0L
            repo.updateNextTriggerMillis(
                reminderId = item.id,
                nextTriggerAtMillis = expectedNext,
                snoozeUntilMillis = validSnooze
            )

            // 2. 如果存在未过期的 10 分钟延后闹钟，同步恢复
            if (validSnooze > now) {
                setAlarmClockFirst(
                    context = context,
                    reminderId = item.id,
                    triggerAtMillis = validSnooze,
                    isSnooze = true,
                    requestCode = requestCodeFor(item.id, isSnooze = true)
                )
            }
        }
        return healedCount
    }

    /**
     * 重新注册所有已开启的提醒（手机重启广播或应用升级广播调用）
     */
    fun rescheduleAll(context: Context) {
        verifyAndHealAll(context)
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

    /**
     * 核心：优先使用 `setAlarmClock()`，在小米 HyperOS / MIUI 中享受系统级闹钟白名单待遇，
     * 并在状态栏显示闹钟图标；若权限异常则逐级回退至 `setExactAndAllowWhileIdle`。
     */
    private fun setAlarmClockFirst(
        context: Context,
        reminderId: String,
        triggerAtMillis: Long,
        isSnooze: Boolean,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operationIntent = buildPendingIntent(context, reminderId, isSnooze, requestCode)

        // 点击状态栏闹钟图标时打开主界面
        val showMainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 500_000,
            showMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExactAlarms(context)) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, operationIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent
                )
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent
                )
            }
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
