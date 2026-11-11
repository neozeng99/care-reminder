package com.care.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.ReminderCategory
import com.care.reminder.ui.AlertActivity
import com.care.reminder.voice.VoiceSpeaker

/**
 * 定时闹钟触发接收器：
 * 1. 自动注册下一次循环/次日闹钟
 * 2. 若属于吃药提醒且今天已提前打过卡，则静默跳过，避免误导老人重复吃药
 * 3. 弹出高优通知 + 全屏强提醒界面 + 中文语音播报
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)

        val repo = ReminderRepository(context)
        val item = repo.getReminderById(reminderId) ?: return
        if (!item.enabled) return

        // 如果是延后闹钟触发，清除 snoozeUntilMillis；如果是常规闹钟触发，自动安排下一次
        if (isSnooze) {
            repo.updateSnoozeUntil(item.id, 0L)
        } else {
            ReminderScheduler.scheduleReminder(context, item)
        }

        // 防重复吃药保护：如果该次吃药今天已经点击过“已完成”，不再重复响铃催促吃药
        val alreadyCompletedToday = repo.getTodayCompletedRecordsFor(item.id).isNotEmpty()
        if (item.category == ReminderCategory.MEDICINE && alreadyCompletedToday && !isSnooze) {
            return
        }

        // 发送带全屏 Intent 和快捷打卡按钮的通知
        NotificationHelper.showReminderNotification(context, item)

        // 语音播报或响铃震动
        if (item.voiceEnabled) {
            VoiceSpeaker.speak(context, item.buildSpeechText(alreadyCompletedToday), vibrate = true)
        } else {
            VoiceSpeaker.triggerVibration(context)
            VoiceSpeaker.playFallbackRingtone(context)
        }

        // 尝试直接拉起全屏强提醒页
        try {
            val alertIntent = Intent(context, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, item.id)
            }
            context.startActivity(alertIntent)
        } catch (_: Exception) {
            // 后台受限时由 Notification FullScreenIntent 接管
        }

        context.sendBroadcast(Intent(NotificationActionReceiver.ACTION_DATA_UPDATED).setPackage(context.packageName))
    }
}
