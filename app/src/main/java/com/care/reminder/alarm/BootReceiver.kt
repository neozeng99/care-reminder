package com.care.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 手机开机或应用覆盖升级后，自动恢复所有启用中的吃药/喝水/久坐闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
