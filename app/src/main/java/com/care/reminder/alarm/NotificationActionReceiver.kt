package com.care.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.reminder.data.ReminderRepository
import com.care.reminder.model.CheckInStatus
import com.care.reminder.voice.VoiceSpeaker

/**
 * 接收通知栏按钮或全屏弹窗的「已完成」与「延后10分钟」动作
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return

        val repo = ReminderRepository(context)
        val item = repo.getReminderById(reminderId) ?: return

        VoiceSpeaker.stopAll()
        NotificationHelper.cancelNotification(context, reminderId)

        when (action) {
            ACTION_COMPLETE -> {
                repo.updateSnoozeUntil(reminderId, 0L)
                repo.recordAction(item, CheckInStatus.COMPLETED)
                // 取消可能存在的延后闹钟，保持常规下一次闹钟
                ReminderScheduler.scheduleReminder(context, item.copy(snoozeUntilMillis = 0L))
            }
            ACTION_SNOOZE_10M -> {
                repo.recordAction(item, CheckInStatus.SNOOZED)
                ReminderScheduler.scheduleSnooze(context, item, minutes = 10)
            }
        }

        context.sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_COMPLETE = "com.care.reminder.ACTION_COMPLETE"
        const val ACTION_SNOOZE_10M = "com.care.reminder.ACTION_SNOOZE_10M"
        const val ACTION_DATA_UPDATED = "com.care.reminder.ACTION_DATA_UPDATED"
    }
}
