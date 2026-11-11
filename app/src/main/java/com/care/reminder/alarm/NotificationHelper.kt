package com.care.reminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.care.reminder.R
import com.care.reminder.model.ReminderItem
import com.care.reminder.ui.AlertActivity

/**
 * 强提醒通知与锁屏全屏弹窗辅助类
 * 通知栏自带两个超大快捷动作按钮：
 * 1. 「✓ 已完成」
 * 2. 「⏰ 延后10分钟」
 */
object NotificationHelper {

    const val CHANNEL_ID = "care_reminder_high_channel"
    private const val CHANNEL_NAME = "健康与吃药强提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于老人吃药、喝水、久坐起身的定时强提醒通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 600)
                setSound(soundUri, audioAttrs)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(context: Context, item: ReminderItem) {
        ensureChannel(context)

        val alertIntent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, item.id)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, item.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (item.id + "_complete").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_10M
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, item.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (item.id + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (item.subtitle.isNotBlank()) {
            "${item.subtitle}（点下方按钮打卡或延后10分钟）"
        } else {
            "完成后请点击「${item.category.actionConfirmText}」记录今日状态"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_care)
            .setContentTitle("${item.category.emoji} ${item.title}")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "✓ ${item.category.actionConfirmText}", completePendingIntent)
            .addAction(0, "⏰ 延后10分钟", snoozePendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationManagerCompat.from(context).notify(notificationIdFor(item.id), notification)
    }

    fun cancelNotification(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(reminderId))
    }

    fun notificationIdFor(reminderId: String): Int {
        return (reminderId.hashCode() and 0x0FFFFFFF) + 5000
    }
}
